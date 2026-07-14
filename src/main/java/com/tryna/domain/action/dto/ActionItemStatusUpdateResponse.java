package com.tryna.domain.action.dto;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ActionItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "E106 준비/실행 항목 상태 변경 응답 DTO")
public record ActionItemStatusUpdateResponse(

        @Schema(
                description = "상태가 변경된 준비/실행 항목 ID",
                example = "1"
        )
        Long actionItemId,

        @Schema(
                description = "준비/실행 항목이 연결된 일정 ID",
                example = "2"
        )
        Long parentEventId,

        @Schema(
                description = "변경된 준비/실행 항목 상태",
                example = "COMPLETED"
        )
        ActionItemStatus actionItemStatus,

        @Schema(
                description = "완료 처리 일시. PENDING으로 되돌린 경우 null입니다.",
                example = "2026-07-11T02:00:00"
        )
        LocalDateTime completedAt

) {

    /**
     * 상태 변경이 완료된 ActionItems 엔티티를
     * E106 응답 DTO로 변환합니다.
     *
     * @param actionItem 상태가 변경된 준비/실행 항목
     * @return E106 상태 변경 응답
     */
    public static ActionItemStatusUpdateResponse from(
            ActionItems actionItem
    ) {
        return new ActionItemStatusUpdateResponse(
                actionItem.getActionItemId(),
                actionItem.getParentEvent().getEventId(),
                actionItem.getActionItemStatus(),
                actionItem.getCompletedAt()
        );
    }
}