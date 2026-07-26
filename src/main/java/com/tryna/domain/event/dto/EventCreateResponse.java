package com.tryna.domain.event.dto;

import com.tryna.domain.action.dto.ActionItemSaveResponse;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import java.time.LocalDateTime;
import java.util.List;

public record EventCreateResponse(
        Long eventId,
        EventStatus status,
        SourceType sourceType,
        LocalDateTime createdAt,
        List<ActionItemSaveResponse.Item> savedActionItems
) {
}
