package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.UpdateScope;
import java.time.LocalDateTime;

public record EventUpdateResponse(
        Long eventId,
        UpdateScope updateScope,
        EventStatus updateStatus,
        Integer affectedEventCount,
        Integer adjustedActionItemCount,
        Boolean requiresActionItemReview,
        Boolean isRecurring,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        RecurrenceDayOfWeek recurrenceDayOfWeek,
        Integer recurrenceDayOfMonth,
        LocalDateTime recurrenceEndDate,
        Long labelId,
        LocalDateTime updatedAt
) {
}
