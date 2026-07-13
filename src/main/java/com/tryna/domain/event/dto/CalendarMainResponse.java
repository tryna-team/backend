package com.tryna.domain.event.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarMainResponse(
        Integer year,
        Integer month,
        LocalDate today,
        LocalDate selectedDate,
        Boolean hasEvents,
        Boolean hasExternalCalendarConnection,
        String emptyStateType,
        List<CalendarMonthlyResponse.DayEventCount> monthlyEventDays,
        List<CalendarDateEventsResponse.EventSummary> selectedDateEvents
) {
}
