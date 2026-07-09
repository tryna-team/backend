package com.tryna.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "인증 토큰 정보 응답 DTO")
public record AuthTokenResponse(
        @Schema(description = "인증 토큰 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "엑세스 토큰", example = "access-token-example")
        String accessToken,

        @Schema(description = "엑세스 토큰 만료까지 남은 시간(초)", example = "1800")
        long accessTokenExpiresInSeconds,

        @Schema(description = "리프레시 토큰", example = "refresh-token-example")
        String refreshToken,

        @Schema(description = "리프레시 토큰 만료 일시", example = "2026-07-18T09:00:00")
        LocalDateTime refreshTokenExpiresAt
) {
}