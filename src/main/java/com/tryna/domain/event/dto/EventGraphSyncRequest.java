package com.tryna.domain.event.dto;

import java.util.List;

public record EventGraphSyncRequest(
        Long eventId,
        Long ownerId,
        Integer eventVersion,
        String eventTitle,
        String activityCode,
        List<EntityDto> entities
) {
    public record EntityDto(
            String name,
            String role
    ) {}
}