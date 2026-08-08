package com.tryna.domain.external.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "G102 외부 캘린더 연동 및 동기화 상태 통합 조회 응답 DTO")
public record CalendarStatusResponse(
        @Schema(description = "외부 캘린더 연동 활성화 여부", example = "true")
        Boolean isConnected,

        @Schema(description = "연동된 제공자", example = "GOOGLE")
        String provider,

        @Schema(description = "연동된 기본 캘린더 이름", example = "google 캘린더")
        String calendarName,

        @Schema(description = "백그라운드 동기화 상태", example = "SUCCESS")
        String syncStatus,

        @Schema(description = "마지막 동기화 수행 일시", example = "2026-07-18T09:00:00")
        LocalDateTime lastSyncedAt,

        @Schema(description = "상태 관련 보조 메시지")
        String message
) {
}