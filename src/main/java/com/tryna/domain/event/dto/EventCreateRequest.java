package com.tryna.domain.event.dto;

import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.event.enums.RecurrenceType;

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
        Boolean isRecurring,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String recurrenceEndDate,
        Long labelId,
        ActionItemSaveRequest actionItems
) {
}
