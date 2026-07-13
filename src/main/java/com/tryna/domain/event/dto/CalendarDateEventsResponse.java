package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import java.time.LocalDate;
import java.util.List;

public record CalendarDateEventsResponse(
        LocalDate date,
        Integer eventCount,
        String emptyStateType,
        List<EventSummary> events
) {

    public record EventSummary(
            Long eventId,
            String title,
            LocalDate startDate,
            String startTime,
            LocalDate endDate,
            String endTime,
            Boolean isAllDay,
            String location,
            SourceType sourceType,
            EventStatus status
    ) {
    }
}
