package com.tryna.domain.auth.service;

import com.tryna.global.redis.RefreshTokenRedisStore;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenPair;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisStore refreshTokenRedisStore;

    // TODO_TOKEN: 비회원 로그인 성공 시 access/refresh token 발급 및 Redis 저장
    public TokenPair issueGuestTokenPair(Long userId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO_TOKEN: 소셜 로그인 성공 시 access/refresh token 발급 및 Redis 저장 (provider별)
    public TokenPair issueSocialTokenPair(Long userId, String provider) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO_TOKEN: refresh token 재발급 — JWT 검증 → Redis 저장값 일치 확인 → Rotation
    public TokenPair reissue(String refreshToken) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO_TOKEN: 로그아웃 — 해당 userId의 모든 provider refresh token 삭제
    public void logout(Long userId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // TODO_TOKEN: 회원 탈퇴 — refresh token 삭제 후 DB 정리
    public void withdraw(Long userId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
