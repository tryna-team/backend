package com.tryna.domain.event.dto;

import com.tryna.domain.action.dto.ActionItemSaveRequest;

public record EventCreateRequest(
        String eventTitle,
        String description,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        Boolean isAllDay,
        String location,
        String eventType,
        ActionItemSaveRequest actionItems
) {
}