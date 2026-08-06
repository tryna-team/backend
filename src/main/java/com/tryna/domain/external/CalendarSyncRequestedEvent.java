package com.tryna.domain.external;

public record CalendarSyncRequestedEvent(
        Long userId,
        Integer targetYear
) {
}