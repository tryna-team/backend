package com.tryna.domain.action.dto;

import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.recommendation.enums.ActionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "E105 action item save request")
public record ActionItemSaveRequest(

        @Schema(description = "Action items to save")
        @NotNull(message = "items must not be null.")
        List<@Valid Item> items,

        @Schema(description = "Recommendation feedback logs")
        @NotNull(message = "feedbackLogs must not be null.")
        List<@Valid Feedback> feedbackLogs

) {

    @Schema(description = "Action item to save")
    public record Item(

            @Schema(description = "Action item title", example = "Prepare gift")
            @NotBlank(message = "Action item title is required.")
            @Size(max = 255, message = "Action item title must be at most 255 characters.")
            String title,

            @Schema(description = "Action item type", example = "TIMED_ACTION")
            @NotNull(message = "Action item type is required.")
            ItemType itemType,

            @Schema(description = "Occurrence date this item belongs to. If omitted, the server uses the parent event start date.", example = "2026-08-25")
            LocalDate occurrenceDate,

            @Schema(description = "Calendar display date", example = "2026-07-03")
            LocalDate displayDate,

            @Schema(description = "Calendar display datetime", example = "2026-07-03T09:00:00")
            LocalDateTime displayTime,

            @Schema(description = "Offset days from parent occurrence date", example = "-7")
            Integer offsetDays,

            @Schema(description = "Creation source", example = "SYSTEM")
            @NotNull(message = "createdBy is required.")
            CreatedBy createdBy,

            @Schema(description = "Recommendation source template ID. User-added items can use null.", example = "gift_prepare_001")
            @Size(max = 100, message = "sourceTemplateId must be at most 100 characters.")
            String sourceTemplateId

    ) {
    }

    @Schema(description = "Recommendation feedback log")
    public record Feedback(

            @Schema(description = "Recommendation source template ID. User-added items can use null.", example = "gift_prepare_001")
            @Size(max = 100, message = "sourceTemplateId must be at most 100 characters.")
            String sourceTemplateId,

            @Schema(description = "User feedback action type", example = "SELECTED")
            @NotNull(message = "feedback actionType is required.")
            ActionType actionType,

            @Schema(description = "Original recommended title", example = "Prepare gift")
            @Size(max = 255, message = "originalTitle must be at most 255 characters.")
            String originalTitle,

            @Schema(description = "Edited title", example = "Edit bouquet message")
            @Size(max = 255, message = "editedTitle must be at most 255 characters.")
            String editedTitle,

            @Schema(description = "Delete or exclude reason", example = "Not needed.")
            @Size(max = 255, message = "reason must be at most 255 characters.")
            String reason

    ) {
    }
}