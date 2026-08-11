package com.tryna.domain.action.dto;

import com.tryna.domain.action.entity.ActionItemOccurrenceStates;
import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "F104 캘린더 내 시간형 실행 항목 조회 응답 DTO")
public record TimedActionItemResponse(

        @Schema(description = "조회한 날짜", example = "2026-07-03")
        LocalDate date,

        @Schema(description = "선택한 날짜에 표시할 시간형 실행 항목 목록")
        List<Item> items

) {

    public static TimedActionItemResponse from(
            LocalDate date,
            List<ActionItems> actionItems
    ) {
        return new TimedActionItemResponse(
                date,
                actionItems.stream()
                        .map(Item::from)
                        .toList()
        );
    }

    public static TimedActionItemResponse of(
            LocalDate date,
            List<Item> items
    ) {
        return new TimedActionItemResponse(date, items);
    }

    @Schema(description = "캘린더에 표시할 시간형 실행 항목")
    public record Item(

            @Schema(description = "준비/실행 항목 ID", example = "10")
            Long actionItemId,

            @Schema(description = "연결된 일정 ID", example = "1")
            Long parentEventId,

            @Schema(description = "항목이 속한 일정 회차 날짜", example = "2026-08-25")
            LocalDate occurrenceDate,

            @Schema(description = "연결된 일정 제목", example = "엄마 생신")
            String parentEventTitle,

            @Schema(description = "준비/실행 항목 제목", example = "꽃다발 준비하기")
            String title,

            @Schema(description = "항목 유형", example = "TIMED_ACTION")
            ItemType itemType,

            @Schema(description = "캘린더 표시 날짜", example = "2026-07-03")
            LocalDate displayDate,

            @Schema(description = "캘린더 표시 시간", example = "2026-07-03T09:00:00")
            LocalDateTime displayTime,

            @Schema(description = "항목 상태", example = "PENDING")
            ActionItemStatus actionItemStatus,

            @Schema(description = "항목 생성 주체", example = "SYSTEM")
            CreatedBy createdBy,

            @Schema(description = "완료 처리 일시. 미완료 상태이면 null", example = "2026-07-01T18:30:12")
            LocalDateTime completedAt

    ) {

        public static Item from(ActionItems actionItem) {
            return new Item(
                    actionItem.getActionItemId(),
                    actionItem.getParentEvent().getEventId(),
                    actionItem.getOccurrenceDate(),
                    actionItem.getParentEvent().getTitle(),
                    actionItem.getTitle(),
                    actionItem.getItemType(),
                    actionItem.getDisplayDate(),
                    actionItem.getDisplayDatetime(),
                    actionItem.getActionItemStatus(),
                    actionItem.getCreatedBy(),
                    actionItem.getCompletedAt()
            );
        }

        public static Item fromOccurrence(
                ActionItems actionItem,
                LocalDate occurrenceDate,
                LocalDate displayDate,
                LocalDateTime displayTime,
                ActionItemOccurrenceStates state
        ) {
            return new Item(
                    actionItem.getActionItemId(),
                    actionItem.getParentEvent().getEventId(),
                    occurrenceDate,
                    actionItem.getParentEvent().getTitle(),
                    actionItem.getTitle(),
                    actionItem.getItemType(),
                    displayDate,
                    displayTime,
                    state == null ? ActionItemStatus.PENDING : state.getActionItemStatus(),
                    actionItem.getCreatedBy(),
                    state == null ? null : state.getCompletedAt()
            );
        }
    }
}
