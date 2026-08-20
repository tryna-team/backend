package com.tryna.domain.action.dto;

import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "F104-2 월간 캘린더 내 시간형 실행 항목 조회 응답 DTO")
public record MonthlyTimedActionItemResponse(

        @Schema(description = "조회 연도", example = "2026")
        Integer year,

        @Schema(description = "조회 월", example = "8")
        Integer month,

        @Schema(description = "조회 월에 표시할 시간형 실행 항목 목록")
        List<Item> items

) {
    @Schema(description = "월간 캘린더에 표시할 시간형 실행 항목")
    public record Item(

            @Schema(description = "준비/실행 항목 ID", example = "10")
            Long actionItemId,

            @Schema(description = "연결된 일정 ID", example = "1")
            Long parentEventId,

            @Schema(description = "항목이 속한 부모 일정 회차 날짜", example = "2026-09-02")
            LocalDate parentOccurrenceDate,

            @Schema(description = "연결된 일정 제목", example = "엄마 생신")
            String parentEventTitle,

            @Schema(description = "현재 사용자의 부모 일정 라벨 ID", example = "110")
            Long labelId,

            @Schema(description = "준비/실행 항목 제목", example = "꽃다발 준비하기")
            String title,

            @Schema(description = "항목 유형", example = "TIMED_ACTION")
            ItemType itemType,

            @Schema(description = "캘린더 표시 날짜", example = "2026-08-30")
            LocalDate displayDate,

            @Schema(description = "캘린더 표시 시간", example = "2026-08-30T09:00:00")
            LocalDateTime displayTime,

            @Schema(description = "항목 상태", example = "PENDING")
            ActionItemStatus actionItemStatus,

            @Schema(description = "항목 생성 주체", example = "SYSTEM")
            CreatedBy createdBy,

            @Schema(description = "완료 처리 일시. 미완료 상태이면 null", example = "2026-08-30T18:30:12")
            LocalDateTime completedAt

    ) {
        public static Item from(
                TimedActionItemResponse.Item item,
                LocalDate parentOccurrenceDate,
                Long labelId
        ) {
            return new Item(
                    item.actionItemId(),
                    item.parentEventId(),
                    parentOccurrenceDate,
                    item.parentEventTitle(),
                    labelId,
                    item.title(),
                    item.itemType(),
                    item.displayDate(),
                    item.displayTime(),
                    item.actionItemStatus(),
                    item.createdBy(),
                    item.completedAt()
            );
        }
    }
}
