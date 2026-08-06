package com.tryna.domain.event.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarMonthlyResponse(
        Integer year,
        Integer month,
        LocalDate today,
        List<DayEventCount> days
) {

    public record DayEventCount(
            LocalDate date,
            Long eventCount,
            Boolean hasEvent,
            List<CalendarDateEventsResponse.EventSummary> previewEvents
    ) {
    }
}
