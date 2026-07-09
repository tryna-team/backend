package com.tryna.domain.auth.service;

import com.tryna.domain.auth.dto.PermissionCheckResponse;
import com.tryna.domain.auth.enums.PermissionAction;
import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.domain.auth.repository.SessionRedisRepository;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenPair;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRedisRepository sessionRedisRepository;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;

    /**
     * A104: 로그인 필요 여부 확인
     */
    @Transactional(readOnly = true)
    public PermissionCheckResponse checkPermission(String actionTypeStr) {
        try {
            PermissionAction action = PermissionAction.valueOf(actionTypeStr.toUpperCase());
            return new PermissionCheckResponse(action.isLoginRequired(), action.getGuideMessage());
        } catch (IllegalArgumentException | NullPointerException e) {
            // 지원하지 않는 액션 타입이거나 null인 경우 A104_PERMISSION_CHECK_400 에러 발생
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.A104_PERMISSION_CHECK_400);
        }
    }

    /**
     * 로그인(GUEST/USER 공통) — session Hash 저장 + fcm Set 추가
     */
    @Transactional
    public TokenPair issueSession(Long userId, String deviceId, String fcmToken, String scopes) {
        // 1. JWT 토큰 발급
        TokenPair tokenPair = jwtTokenProvider.generateTokenPair(userId);

        // 2. 만료 시간(TTL) 계산
        long refreshExpirationSeconds = jwtTokenProvider.getRefreshExpirationSeconds();
        Duration ttl = Duration.ofSeconds(refreshExpirationSeconds);

        // 3. Redis 세션 Hash 저장 (기기별 Hash 업데이트 및 TTL 설정)
        sessionRedisRepository.save(
                userId,
                deviceId,
                tokenPair.refreshToken(),
                fcmToken,
                scopes,
                Instant.now(),
                ttl
        );

        // 4. Redis FCM Set에 토큰 추가 (FCM 토큰이 존재하는 경우에만)
        if (fcmToken != null && !fcmToken.isBlank()) {
            fcmTokenRedisRepository.add(userId, fcmToken, ttl);
        }

        return tokenPair;
    }

    // TODO: refresh — token_value 일치 검증 후 Rotation 및 TTL 연장
    public TokenPair reissue(Long userId, String deviceId, String refreshToken) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO: 로그아웃 — session 삭제 + fcm Set에서 제거
    public void logout(Long userId, String deviceId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO: 앱 실행 시 FCM 토큰만 갱신 (session Hash + fcm Set 동기화)
    public void updateFcmToken(Long userId, String deviceId, String fcmToken) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO: 회원 탈퇴 — userId 기준 session/fcm Redis 데이터 전체 삭제 후 DB 정리
    public void withdraw(Long userId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
