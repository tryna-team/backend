package com.tryna.domain.event.dto;

import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.UpdateScope;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        Boolean isRecurring,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String recurrenceEndDate,
        String occurrenceDate,
        UpdateScope updateScope,
        ActionItems actionItems
) {

    public record ActionItems(
            List<Item> items,
            List<Long> deletedActionItemIds
    ) {
    }

    public record Item(
            Long actionItemId,
            String title,
            ItemType itemType,
            LocalDate occurrenceDate,
            LocalDate displayDate,
            LocalDateTime displayTime,
            Integer offsetDays,
            ActionItemStatus actionItemStatus,
            CreatedBy createdBy,
            String sourceTemplateId
    ) {
    }
}
