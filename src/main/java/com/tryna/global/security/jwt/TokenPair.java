package com.tryna.global.security.jwt;

public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
