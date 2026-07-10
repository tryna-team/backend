package com.tryna.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A105 소셜 로그인 및 회원가입 응답 DTO")
public record AuthSessionResponse(

        @Schema(description = "사용자 식별자", example = "2")
        Long userId,

        @Schema(description = "시스템 사용자 권한", example = "USER")
        String userRole,

        @Schema(description = "신규 회원가입 여부", example = "true")
        boolean isNewUser,

        @Schema(description = "발급된 토큰 정보")
        AuthTokenResponse auth
) {
}