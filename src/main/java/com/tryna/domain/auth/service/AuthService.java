package com.tryna.domain.auth.service;

import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.domain.auth.repository.SessionRedisRepository;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenPair;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRedisRepository sessionRedisRepository;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;

    // TODO: 로그인(GUEST/USER 공통) — session Hash 저장 + fcm Set 추가
    public TokenPair issueSession(Long userId, String deviceId, String fcmToken, String scopes) {
        throw new UnsupportedOperationException("Not implemented");
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
