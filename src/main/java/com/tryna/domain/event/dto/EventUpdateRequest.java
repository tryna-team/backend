package com.tryna.domain.event.dto;

import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.UpdateScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "C107 일정 수정 요청 DTO")
public record EventUpdateRequest(

        @Schema(description = "수정할 제목", example = "데이터베이스 기말고사")
        String eventTitle,
        String description,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        Boolean isAllDay,
        String location,
        Long labelId,
        Boolean isRecurring,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String recurrenceEndDate,
        String occurrenceDate,
        UpdateScope updateScope,
        ActionItems actionItems,

        @Schema(description = "수정된 M1 Activity 코드 (변경 없으면 기존 값 유지)", example = "TAKE_EXAM")
        String activityCode,

        @Schema(description = "수정된 M1 Entity 리스트")
        List<EntityDto> entities
) {

    public record ActionItems(
            List<Item> items,
            List<Long> deletedActionItemIds
    ) {
    }

    public record Item(
            Long actionItemId,
            String title,
            ItemType itemType,
            LocalDate occurrenceDate,
            LocalDate displayDate,
            LocalDateTime displayTime,
            Integer offsetDays,
            ActionItemStatus actionItemStatus,
            CreatedBy createdBy,
            @Schema(description = "Neo4j Rule ID (과거 sourceTemplateId)", example = "45")
            String sourceTemplateId
    ) {}
    @Schema(description = "수정할 개체 정보")
    public record EntityDto(
            @Schema(description = "개체명", example = "데이터베이스")
            String name,

            @Schema(description = "개체 역할", example = "SUBJECT")
            String role
    ) {}
}
