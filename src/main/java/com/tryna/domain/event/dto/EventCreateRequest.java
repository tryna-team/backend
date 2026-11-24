package com.tryna.domain.event.dto;

import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.event.enums.RecurrenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "C104 일정 생성 요청 DTO")
public record EventCreateRequest(

        @Schema(description = "일정 제목", example = "데이터베이스 중간고사")
        String eventTitle,

        @Schema(description = "일정 설명", example = "가천관 414호 대면 시험 (범위: 트랜잭션까지)")
        String description,

        @Schema(description = "일정 시작 날짜 (yyyy-MM-dd)", example = "2026-10-20")
        String startDate,

        @Schema(description = "일정 시작 시간 (HH:mm)", example = "13:00")
        String startTime,

        @Schema(description = "일정 종료 날짜 (yyyy-MM-dd)", example = "2026-10-20")
        String endDate,

        @Schema(description = "일정 종료 시간 (HH:mm)", example = "15:00")
        String endTime,

        @Schema(description = "종일 일정 여부", example = "false")
        Boolean isAllDay,

        @Schema(description = "일정 장소", example = "가천관 414호")
        String location,

        @Schema(description = "일정 유형", example = "시험")
        String eventType,

        @Schema(description = "반복 일정 여부", example = "false")
        Boolean isRecurring,

        @Schema(description = "반복 유형", example = "NONE")
        RecurrenceType recurrenceType,

        @Schema(description = "반복 간격. 반복 일정일 경우 1 이상", example = "1")
        Integer recurrenceInterval,

        @Schema(description = "반복 종료 날짜 (yyyy-MM-dd)", example = "2026-10-20")
        String recurrenceEndDate,

        @Schema(description = "라벨 ID. 미입력 시 기본 라벨 사용", example = "1")
        Long labelId,

        @Schema(description = "일정과 함께 저장할 준비/실행 항목 및 피드백")
        ActionItemSaveRequest actionItems,

        @Schema(description = "AI가 분석한 M1 Activity 코드 (파싱 실패 시 null 허용)", example = "TAKE_EXAM")
        String activityCode,

        @Schema(description = "AI가 분석한 M1 Entity 리스트")
        List<EntityRequest> entities
) {
        @Schema(description = "일정 내 연관 개체 정보")
        public record EntityRequest(
                @Schema(description = "개체명", example = "데이터베이스")
                String name,

                @Schema(description = "개체 역할", example = "SUBJECT")
                String role
        ) {}
}