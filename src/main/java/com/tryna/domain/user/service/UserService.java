package com.tryna.domain.user.service;

import com.tryna.domain.auth.dto.AuthTokenResponse;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.domain.user.dto.GuestCreateRequest;
import com.tryna.domain.user.dto.GuestCreateResponse;
import com.tryna.domain.user.dto.UserStatusResponse;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    // private final UserEventRepository userEventRepository;
    // private final ExternalCalendarConnectionRepository externalCalendarConnectionRepository;

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

    // --- Helper Method ---

    private AuthTokenResponse mapToAuthTokenResponse(TokenPair tokenPair) {
        // MVP: accessToken 만료시간은 JwtProperties 값을 직접 가져오거나 기본값 적용
        // (추후 JwtTokenProvider에 getAccessExpirationSeconds() 추가 권장)
        long refreshExpiration = jwtTokenProvider.getRefreshExpirationSeconds();
        long accessExpiration = jwtTokenProvider.getAccessExpirationSeconds();

        return new AuthTokenResponse(
                "Bearer",
                tokenPair.accessToken(),
                accessExpiration,
                tokenPair.refreshToken(),
                LocalDateTime.now().plusSeconds(refreshExpiration)
        );
    }

    // Service 내부에서 HTTP 상태 코드 분기를 돕기 위한 내부 Record
    public record GuestResult(boolean isNew, GuestCreateResponse response) {}
}
