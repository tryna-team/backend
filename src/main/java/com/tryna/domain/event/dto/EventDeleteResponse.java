package com.tryna.domain.event.dto;

import com.tryna.domain.event.enums.DeleteScope;
import com.tryna.domain.event.enums.EventStatus;

public record EventDeleteResponse(
        Long eventId,
        DeleteScope deleteScope,
        EventStatus deletionStatus,
        Integer affectedEventCount,
        Integer affectedActionItemCount
) {
}
