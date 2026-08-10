package com.tryna.domain.action.dto;

import com.tryna.domain.action.enums.ActionItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "E106 action item status update request")
public record ActionItemStatusUpdateRequest(

        @Schema(description = "Recurring occurrence date to update. Required only when updating a recurring event occurrence.", example = "2026-08-25")
        LocalDate occurrenceDate,

        @Schema(description = "Status to apply", example = "COMPLETED")
        @NotNull(message = "Action item status is required.")
        ActionItemStatus actionItemStatus

) {
}