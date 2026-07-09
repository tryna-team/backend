package com.tryna.domain.user.dto;

import com.tryna.domain.auth.dto.AuthTokenResponse;
import com.tryna.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "비회원 생성 응답 DTO")
public record GuestCreateResponse(
        @Schema(description = "생성된 임시 사용자 식별자", example = "1")
        Long userId,

        @Schema(description = "시스템 사용자 권한", example = "GUEST")
        UserRole userRole,

        @Schema(description = "비회원 인증 및 API 호출에 사용할 토큰 정보")
        AuthTokenResponse auth
) {
}
