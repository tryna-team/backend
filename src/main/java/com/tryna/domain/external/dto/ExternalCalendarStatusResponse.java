package com.tryna.domain.external.dto;

public record ExternalCalendarStatusResponse(
        boolean isConnected,
        String provider,
        String calendarName
) {}