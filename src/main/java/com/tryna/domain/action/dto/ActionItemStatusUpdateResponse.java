package com.tryna.domain.action.dto;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ActionItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "E106 action item status update response")
public record ActionItemStatusUpdateResponse(

        @Schema(description = "Action item ID", example = "1")
        Long actionItemId,

        @Schema(description = "Parent event ID", example = "10")
        Long parentEventId,

        @Schema(description = "Occurrence date whose status was updated", example = "2026-08-25")
        LocalDate occurrenceDate,

        @Schema(description = "Updated status", example = "COMPLETED")
        ActionItemStatus actionItemStatus,

        @Schema(description = "Completed datetime. Null when status is PENDING.", example = "2026-08-25T09:00:00")
        LocalDateTime completedAt

) {

    public static ActionItemStatusUpdateResponse from(ActionItems actionItem) {
        return from(
                actionItem,
                actionItem.getOccurrenceDate(),
                actionItem.getActionItemStatus(),
                actionItem.getCompletedAt()
        );
    }

    public static ActionItemStatusUpdateResponse from(
            ActionItems actionItem,
            LocalDate occurrenceDate,
            ActionItemStatus actionItemStatus,
            LocalDateTime completedAt
    ) {
        return new ActionItemStatusUpdateResponse(
                actionItem.getActionItemId(),
                actionItem.getParentEvent().getEventId(),
                occurrenceDate,
                actionItemStatus,
                completedAt
        );
    }
}