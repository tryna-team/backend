package com.tryna.domain.user.dto;

import com.tryna.domain.auth.dto.AuthTokenResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A106 회원 전환 유도 응답 DTO")
public record UserConversionResponse(

        @Schema(description = "사용자 식별자", example = "2")
        Long userId,

        @Schema(description = "시스템 사용자 권한", example = "USER")
        String userRole,

        @Schema(description = "발급된 정식 회원용 토큰 정보")
        AuthTokenResponse auth
) {
}