package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.UpdateScope;

public record EventUpdateRequest(
        String eventTitle,
        String description,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        Boolean isAllDay,
        String location,
        Long labelId,
        String occurrenceDate,
        UpdateScope updateScope
) {
}
