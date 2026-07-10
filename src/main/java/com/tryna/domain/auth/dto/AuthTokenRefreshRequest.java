package com.tryna.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "A108 토큰 갱신 요청 DTO")
public record AuthTokenRefreshRequest(
        @Schema(description = "접속 기기 식별자", example = "device-uuid-1234")
        @NotBlank(message = "deviceId는 필수값입니다.")
        String deviceId,

        @Schema(description = "기존에 발급받은 리프레시 토큰")
        @NotBlank(message = "refreshToken은 필수값입니다.")
        String refreshToken
) {
}