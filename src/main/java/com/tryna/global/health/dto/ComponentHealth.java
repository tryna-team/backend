package com.tryna.global.health.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개별 인프라 컴포넌트 상태")
public record ComponentHealth(
        @Schema(description = "컴포넌트 상태", example = "UP")
        HealthStatus status,

        @Schema(description = "상태 세부 메시지 (정상이면 null)", example = "null")
        String detail
) {
    public static ComponentHealth up() {
        return new ComponentHealth(HealthStatus.UP, null);
    }

    public static ComponentHealth down(String detail) {
        return new ComponentHealth(HealthStatus.DOWN, detail);
    }

    public boolean isUp() {
        return status == HealthStatus.UP;
    }
}
