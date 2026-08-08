package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.UpdateScope;
import java.time.LocalDateTime;

public record EventUpdateResponse(
        Long eventId,
        UpdateScope updateScope,
        EventStatus updateStatus,
        Integer affectedEventCount,
        Integer adjustedActionItemCount,
        Boolean requiresActionItemReview,
        Long labelId,
        LocalDateTime updatedAt
) {
}
