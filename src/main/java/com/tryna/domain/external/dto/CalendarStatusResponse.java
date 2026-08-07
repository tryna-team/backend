package com.tryna.domain.external.dto;

import java.time.LocalDateTime;

public record CalendarStatusResponse(
        Boolean isLinked,
        String syncStatus,
        LocalDateTime lastSyncedAt,
        String message
) {
}
