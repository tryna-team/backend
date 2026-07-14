package com.tryna.domain.event.dto;

public record EventCreateRequest(
        String sourceText,
        String title,
        String description,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        Boolean isAllDay,
        String location,
        String eventTypeCandidate
) {
}
