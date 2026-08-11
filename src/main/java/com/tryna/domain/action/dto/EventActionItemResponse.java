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
import java.util.Map;

@Schema(description = "F103 일정 상세 내 준비/실행 항목 조회 응답 DTO")
public record EventActionItemResponse(

        @Schema(description = "조회한 일정 ID", example = "2")
        Long eventId,

        @Schema(description = "일정에 연결된 준비/실행 항목 목록")
        List<Item> items

) {

    public static EventActionItemResponse from(
            Long eventId,
            List<ActionItems> actionItems
    ) {
        return new EventActionItemResponse(
                eventId,
                actionItems.stream()
                        .map(Item::from)
                        .toList()
        );
    }

    public static EventActionItemResponse fromOccurrence(
            Long eventId,
            List<ActionItems> actionItems,
            LocalDate occurrenceDate,
            Map<Long, ActionItemOccurrenceStates> statesByActionItemId
    ) {
        return new EventActionItemResponse(
                eventId,
                actionItems.stream()
                        .map(actionItem -> Item.fromOccurrence(
                                actionItem,
                                occurrenceDate,
                                statesByActionItemId.get(actionItem.getActionItemId())
                        ))
                        .toList()
        );
    }

    @Schema(description = "일정에 연결된 준비/실행 항목")
    public record Item(

            @Schema(description = "준비/실행 항목 ID", example = "1")
            Long actionItemId,

            @Schema(description = "준비/실행 항목 제목", example = "선물 준비하기")
            String title,

            @Schema(description = "항목 유형", example = "TIMED_ACTION")
            ItemType itemType,

            @Schema(description = "항목이 속한 일정 회차 날짜", example = "2026-08-25")
            LocalDate occurrenceDate,

            @Schema(description = "캘린더 표시 날짜", example = "2026-07-15")
            LocalDate displayDate,

            @Schema(description = "캘린더 표시 일시", example = "2026-07-15T09:00:00")
            LocalDateTime displayTime,

            @Schema(description = "부모 일정 기준 상대 일수", example = "-3")
            Integer offsetDays,

            @Schema(description = "항목 상태", example = "PENDING")
            ActionItemStatus actionItemStatus,

            @Schema(description = "항목 생성 주체", example = "SYSTEM")
            CreatedBy createdBy,

            @Schema(description = "추천 원본 템플릿 ID", example = "gift_prepare_001")
            String sourceTemplateId,

            @Schema(description = "완료 처리 일시. 미완료 상태이면 null", example = "2026-07-11T03:00:00")
            LocalDateTime completedAt

    ) {

        public static Item from(ActionItems actionItem) {
            return new Item(
                    actionItem.getActionItemId(),
                    actionItem.getTitle(),
                    actionItem.getItemType(),
                    actionItem.getOccurrenceDate(),
                    actionItem.getDisplayDate(),
                    actionItem.getDisplayDatetime(),
                    actionItem.getOffsetDays(),
                    actionItem.getActionItemStatus(),
                    actionItem.getCreatedBy(),
                    actionItem.getSourceTemplateId(),
                    actionItem.getCompletedAt()
            );
        }

        public static Item fromOccurrence(
                ActionItems actionItem,
                LocalDate occurrenceDate,
                ActionItemOccurrenceStates state
        ) {
            LocalDate displayDate = resolveDisplayDate(actionItem, occurrenceDate);
            LocalDateTime displayTime = resolveDisplayTime(actionItem, displayDate);

            return new Item(
                    actionItem.getActionItemId(),
                    actionItem.getTitle(),
                    actionItem.getItemType(),
                    occurrenceDate,
                    displayDate,
                    displayTime,
                    actionItem.getOffsetDays(),
                    resolveStatus(state),
                    actionItem.getCreatedBy(),
                    actionItem.getSourceTemplateId(),
                    resolveCompletedAt(state)
            );
        }

        private static LocalDate resolveDisplayDate(ActionItems actionItem, LocalDate occurrenceDate) {
            if (actionItem.getItemType() == ItemType.TIMED_ACTION
                    && actionItem.getOffsetDays() != null
                    && occurrenceDate != null) {
                return occurrenceDate.plusDays(actionItem.getOffsetDays());
            }

            return actionItem.getDisplayDate();
        }

        private static LocalDateTime resolveDisplayTime(ActionItems actionItem, LocalDate displayDate) {
            if (actionItem.getDisplayDatetime() == null || displayDate == null) {
                return actionItem.getDisplayDatetime();
            }

            return LocalDateTime.of(displayDate, actionItem.getDisplayDatetime().toLocalTime());
        }

        private static ActionItemStatus resolveStatus(
                ActionItemOccurrenceStates state
        ) {
            return state == null
                    ? ActionItemStatus.PENDING
                    : state.getActionItemStatus();
        }

        private static LocalDateTime resolveCompletedAt(
                ActionItemOccurrenceStates state
        ) {
            return state == null
                    ? null
                    : state.getCompletedAt();
        }
    }
}
