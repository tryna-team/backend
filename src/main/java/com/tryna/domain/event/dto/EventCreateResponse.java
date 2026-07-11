package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import java.time.LocalDateTime;

public record EventCreateResponse(
        Long eventId,
        EventStatus status,
        SourceType sourceType,
        LocalDateTime createdAt
) {
}
