package com.tryna.domain.event.dto;

import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.event.enums.RecurrenceType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "C104 일정 생성 요청 DTO")
public record EventCreateRequest(

        @Schema(description = "일정 제목", example = "매일 운동")
        String eventTitle,

        @Schema(description = "일정 설명", example = "헬스장에서 운동하기")
        String description,

        @Schema(description = "일정 시작 날짜 (yyyy-MM-dd)", example = "2026-08-24")
        String startDate,

        @Schema(description = "일정 시작 시간 (HH:mm)", example = "09:00")
        String startTime,

        @Schema(description = "일정 종료 날짜 (yyyy-MM-dd)", example = "2026-08-24")
        String endDate,

        @Schema(description = "일정 종료 시간 (HH:mm)", example = "10:00")
        String endTime,

        @Schema(description = "종일 일정 여부", example = "false")
        Boolean isAllDay,

        @Schema(description = "일정 장소", example = "헬스장")
        String location,

        @Schema(description = "일정 유형", example = "운동")
        String eventType,

        @Schema(description = "반복 일정 여부", example = "true")
        Boolean isRecurring,

        @Schema(description = "반복 유형", example = "DAILY")
        RecurrenceType recurrenceType,

        @Schema(description = "반복 간격. 반복 일정일 경우 1 이상", example = "1")
        Integer recurrenceInterval,

        @Schema(description = "반복 종료 날짜 (yyyy-MM-dd)", example = "2026-08-26")
        String recurrenceEndDate,

        @Schema(description = "라벨 ID. 미입력 시 기본 라벨 사용", example = "1")
        Long labelId,

        @Schema(description = "일정과 함께 저장할 준비/실행 항목 및 피드백")
        ActionItemSaveRequest actionItems

) {
}