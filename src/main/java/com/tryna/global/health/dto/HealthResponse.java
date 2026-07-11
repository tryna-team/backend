package com.tryna.global.health.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "상세 헬스체크 응답 DTO")
public record HealthResponse(
        @Schema(description = "전체 상태", example = "UP")
        HealthStatus status,

        @Schema(description = "점검 시각 (UTC)", example = "2026-07-11T13:20:00Z")
        Instant timestamp,

        @Schema(description = "인프라 컴포넌트별 상태")
        Components components
) {
    @Schema(description = "컴포넌트별 상태 묶음")
    public record Components(
            @Schema(description = "PostgreSQL(RDS) 연결 상태")
            ComponentHealth database,

            @Schema(description = "Redis(ElastiCache) 연결 상태")
            ComponentHealth redis
    ) {
    }

    public static HealthResponse of(ComponentHealth database, ComponentHealth redis) {
        HealthStatus overall = (database.isUp() && redis.isUp()) ? HealthStatus.UP : HealthStatus.DOWN;
        return new HealthResponse(overall, Instant.now(), new Components(database, redis));
    }

    public boolean isUp() {
        return status == HealthStatus.UP;
    }
}
