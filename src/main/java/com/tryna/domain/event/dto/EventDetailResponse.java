package com.tryna.domain.event.dto;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import java.time.LocalDate;

public record EventDetailResponse(
        Long eventId,
        String eventTitle,
        String description,
        LocalDate startDate,
        String startTime,
        LocalDate endDate,
        String endTime,
        Boolean isAllDay,
        String location,
        String eventTypeCandidate,
        String eventType,
        SourceType sourceType,
        EventStatus status,
        String externalEventId,
        Provider provider
) {
}
