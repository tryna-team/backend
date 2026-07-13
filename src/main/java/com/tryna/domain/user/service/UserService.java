package com.tryna.domain.user.service;

import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.auth.dto.AuthTokenResponse;
import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.domain.auth.repository.SessionRedisRepository;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.domain.event.repository.EventAnalysisLogsRepository;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.domain.external.repository.ExternalCalendarConnectionsRepository;
import com.tryna.domain.external.repository.ExternalCalendarsRepository;
import com.tryna.domain.recommendation.repository.RecommendationFeedbacksRepository;
import com.tryna.domain.reminder.repository.RemindersRepository;
import com.tryna.domain.term.repository.UserAgreedTermsRepository;
import com.tryna.domain.user.dto.*;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.entity.UserSettings;
import com.tryna.domain.user.enums.UserRole;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.domain.user.repository.UserSettingsRepository;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.UserErrorCode;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenPair;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    // --- [Services & Providers] ---
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    // --- [JPA Repositories] ---
    private final ActionItemsRepository actionItemsRepository;
    private final AuthsRepository authsRepository;
    private final EventAnalysisLogsRepository eventAnalysisLogsRepository;
    private final EventsRepository eventsRepository;
    private final ExternalCalendarConnectionsRepository externalCalendarConnectionsRepository;
    private final ExternalCalendarsRepository externalCalendarsRepository;
    private final RecommendationFeedbacksRepository recommendationFeedbacksRepository;
    private final RemindersRepository remindersRepository;
    private final UserAgreedTermsRepository userAgreedTermsRepository;
    private final UserEventsRepository userEventsRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    // --- [Redis Repositories] ---
    private final FcmTokenRedisRepository fcmTokenRedisRepository;
    private final SessionRedisRepository sessionRedisRepository;

    /**
     * A101: 앱 진입 상태 조회
     */
    @Transactional(readOnly = true)
    public UserStatusResponse getUserStatus(Long userId) {
        // 1. 기기에 토큰이 없거나 만료된 상태 (인증 객체 없음)
        if (userId == null) {
            return new UserStatusResponse(UserRole.NONE, false, false);
        }

        // 2. 토큰이 유효한 경우 유저 조회
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        // UserRepository의 네이티브 쿼리를 활용하여 실제 DB 상태 조회
        boolean hasEvents = userRepository.hasActiveEvents(userId);
        boolean hasExternalCalendarConnection = userRepository.hasExternalCalendarConnection(userId);

        return new UserStatusResponse(user.getUserRole(), hasEvents, hasExternalCalendarConnection);
    }

    /**
     * A102: 비회원 시작 (신규 생성 or 기존 재접속)
     */
    @Transactional
    public GuestResult createOrLoginGuest(GuestCreateRequest request) {
        Optional<Users> existingGuest = userRepository.findByGuestIdAndDeletedAtIsNull(request.guestId());

        Users targetUser;
        boolean isNewUser;

        if (existingGuest.isPresent()) {
            targetUser = existingGuest.get();
            isNewUser = false;
        } else {
            targetUser = Users.createGuest(request.guestId());
            userRepository.save(targetUser);

            UserSettings settings = UserSettings.createDefault(targetUser);
            userSettingsRepository.save(settings);

            isNewUser = true;
        }

        // Redis 세션 생성 및 토큰 발급 (GUEST 권한)
        TokenPair tokenPair = authService.issueSession(
                targetUser.getUserId(),
                request.guestId(), // 비회원은 guestId를 deviceId로 사용
                request.fcmToken(),
                UserRole.GUEST.name()
        );

        // AuthTokenResponse 매핑
        AuthTokenResponse authTokenResponse = mapToAuthTokenResponse(tokenPair);

        GuestCreateResponse response = new GuestCreateResponse(
                targetUser.getUserId(),
                targetUser.getUserRole(),
                authTokenResponse
        );

        return new GuestResult(isNewUser, response);
    }

    /**
     * G103: 계정 정보 확인
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        // 1. 유저 조회 (deleted_at IS NULL 조건, 없으면 404)
        Users user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.G103_USER_PROFILE_404));

        // 2. 소셜 연동 정보 조회 (deleted_at IS NULL 조건)
        List<Auths> activeAuths = authsRepository.findAllByUser_UserIdAndDeletedAtIsNull(userId);
        List<UserProfileResponse.LinkedAuthDto> linkedAuthDtos = activeAuths.stream()
                .map(auth -> new UserProfileResponse.LinkedAuthDto(auth.getProvider().name(), auth.getEmail()))
                .toList();

        // 3. 외부 캘린더 연동 여부 확인
        boolean hasConnection = externalCalendarConnectionsRepository.existsByUser_UserIdAndConnectionStatus(userId, ConnectionStatus.ACTIVE);

        // 4. 응답 DTO 조립
        return new UserProfileResponse(
                user.getUserId(),
                user.getUserRole().name(),
                user.getNickname(),
                user.getCreatedAt().toString(),
                linkedAuthDtos,
                hasConnection
        );
    }

    /**
     * G104: 데이터 삭제 (회원 탈퇴)
     */
    @Transactional
    public void withdraw(Long userId) {
        // 1. 유저 검증
        if (userRepository.findByUserIdAndDeletedAtIsNull(userId).isEmpty()) {
            throw new BusinessException(UserErrorCode.G103_USER_PROFILE_404);
        }

        LocalDateTime deletedAt = LocalDateTime.now();

        // --- [Step 1] Events & ActionItems Soft Delete (가장 먼저!) ---
        actionItemsRepository.softDeleteByUserId(userId, deletedAt);
        eventsRepository.softDeleteByUserId(userId, deletedAt);

        // --- [Step 2] UserEvents를 참조하는 자식 테이블 Hard Delete ---
        eventAnalysisLogsRepository.deleteByUserId(userId);

        // --- [Step 3] 부모/자식 테이블 Hard Delete ---
        remindersRepository.deleteByUserId(userId);
        recommendationFeedbacksRepository.deleteByUserId(userId);
        userAgreedTermsRepository.deleteByUserId(userId);

        // --- [Step 4] 외부 캘린더 도메인 Hard Delete ---
        // Step 1에서 Events가 이미 Soft Delete 되었으므로, SET_NULL 시 Check 에러 발생 안 함
        externalCalendarsRepository.deleteByUserId(userId);
        externalCalendarConnectionsRepository.deleteByUserId(userId);

        // --- [Step 5] 다리(매핑 테이블) 폭파 (Hard Delete) ---
        userEventsRepository.deleteByUserId(userId);

        // --- [Step 6] 유저 본체와 직접 연결된 것들 Soft Delete ---
        userSettingsRepository.softDeleteByUserId(userId, deletedAt);
        authsRepository.softDeleteByUserId(userId, deletedAt);
        userRepository.softDeleteByUserId(userId, deletedAt);

        // --- [Step 7] Redis 캐시 및 FCM 토큰 파기 ---
        sessionRedisRepository.deleteAllByUserId(userId);
        fcmTokenRedisRepository.deleteAllByUserId(userId);
    }

    // --- Helper Method ---

    private AuthTokenResponse mapToAuthTokenResponse(TokenPair tokenPair) {
        long refreshExpiration = jwtTokenProvider.getRefreshExpirationSeconds();
        long accessExpiration = jwtTokenProvider.getAccessExpirationSeconds();

        String refreshTokenExpiresAt = java.time.Instant.now()
                .plusSeconds(refreshExpiration)
                .toString();

        return new AuthTokenResponse(
                "Bearer",
                tokenPair.accessToken(),
                accessExpiration,
                tokenPair.refreshToken(),
                refreshTokenExpiresAt
        );
    }

    @Transactional
    public FeedBackDataSettingResponse feedBackDataSetting(Long userId, FeedBackDataSettingRequest request) {
        Users user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        Boolean result = user.getUserSettings().feedBackDataSetting(request.isFeedbackDataCollected());

        return FeedBackDataSettingResponse.builder()
                .isFeedbackDataCollected(result)
                .build();
    }

    // Service 내부에서 HTTP 상태 코드 분기를 돕기 위한 내부 Record
    public record GuestResult(boolean isNew, GuestCreateResponse response) {}
}
