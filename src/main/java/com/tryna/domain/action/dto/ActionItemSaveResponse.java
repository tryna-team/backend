package com.tryna.domain.action.dto;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "E105 준비/실행 항목 일괄 저장 응답 DTO")
public record ActionItemSaveResponse(

        @Schema(
                description = "준비/실행 항목이 저장된 일정 ID",
                example = "1"
        )
        Long eventId,

        @Schema(description = "저장된 준비/실행 항목 목록")
        List<Item> items

) {

    /**
     * 저장된 ActionItems 엔티티 목록을 E105 응답 DTO로 변환합니다.
     *
     * @param eventId     준비/실행 항목이 연결된 일정 ID
     * @param actionItems 저장된 준비/실행 항목 엔티티 목록
     * @return E105 준비/실행 항목 저장 응답
     */
    public static ActionItemSaveResponse from(
            Long eventId,
            List<ActionItems> actionItems
    ) {
        return new ActionItemSaveResponse(
                eventId,
                actionItems.stream()
                        .map(Item::from)
                        .toList()
        );
    }

    /**
     * E105에서 저장된 준비/실행 항목 하나를 표현합니다.
     */
    @Schema(description = "저장된 준비/실행 항목")
    public record Item(

            @Schema(
                    description = "생성된 준비/실행 항목 ID",
                    example = "10"
            )
            Long actionItemId,

            @Schema(
                    description = "준비/실행 항목이 연결된 일정 ID",
                    example = "1"
            )
            Long parentEventId,

            @Schema(
                    description = "준비/실행 항목 제목",
                    example = "선물 준비하기"
            )
            String title,

            @Schema(
                    description = "항목 유형",
                    example = "timed_action"
            )
            ItemType itemType,

            @Schema(
                    description = "캘린더에 표시할 날짜",
                    example = "2026-07-03"
            )
            LocalDate displayDate,

            @Schema(
                    description = "캘린더에 표시할 날짜와 시간",
                    example = "2026-07-03T09:00:00"
            )
            LocalDateTime displayTime,

            @Schema(
                    description = "준비/실행 항목 상태",
                    example = "PENDING"
            )
            ActionItemStatus actionItemStatus,

            @Schema(
                    description = "항목 생성 주체",
                    example = "SYSTEM"
            )
            CreatedBy createdBy

    ) {

        /**
         * ActionItems 엔티티를 응답 항목 DTO로 변환합니다.
         *
         * @param actionItem 저장된 준비/실행 항목 엔티티
         * @return E105 응답에 포함할 항목 정보
         */
        private static Item from(ActionItems actionItem) {
            return new Item(
                    actionItem.getActionItemId(),
                    actionItem.getParentEvent().getEventId(),
                    actionItem.getTitle(),
                    actionItem.getItemType(),
                    actionItem.getDisplayDate(),
                    actionItem.getDisplayDatetime(),
                    actionItem.getActionItemStatus(),
                    actionItem.getCreatedBy()
            );
        }
    }
}