package com.tryna.domain.user.service;

import com.tryna.domain.auth.dto.AuthTokenResponse;
import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.repository.AuthsRepository;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.domain.auth.service.GuestSignupService;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.domain.external.repository.ExternalCalendarConnectionsRepository;
import com.tryna.domain.user.dto.*;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.enums.UserRole;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.UserErrorCode;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    // --- [Services & Providers] ---
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final GuestSignupService guestSignupService;

    // --- [JPA Repositories] ---
    private final AuthsRepository authsRepository;
    private final ExternalCalendarConnectionsRepository externalCalendarConnectionsRepository;
    private final UserRepository userRepository;

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
            // 독립된 트랜잭션으로 유저, 설정, 라벨을 DB에 완벽한 한 세트로 커밋
            targetUser = guestSignupService.registerNewGuest(request.guestId());
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
