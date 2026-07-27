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

@Schema(description = "E105 준비/실행 항목 일괄 저장 요청 DTO")
public record ActionItemSaveRequest(

        @Schema(description = "최종 저장할 준비/실행 항목 목록")
        @NotNull(message = "준비/실행 항목 목록은 null일 수 없습니다.")
        List<@Valid Item> items,

        @Schema(description = "사용자의 제안 항목 선택·수정·삭제·추가 피드백 로그 목록")
        @NotNull(message = "피드백 로그 목록은 null일 수 없습니다.")
        List<@Valid Feedback> feedbackLogs

) {

    /**
     * E105에서 실제로 저장할 준비/실행 항목 하나를 표현합니다.
     *
     * 프론트엔드에서 E101~E104 과정을 거쳐 최종 확정된 항목만 전달합니다.
     */
    @Schema(description = "저장할 준비/실행 항목")
    public record Item(

            @Schema(description = "준비/실행 항목 제목", example = "선물 준비하기")
            @NotBlank(message = "준비/실행 항목 제목은 필수입니다.")
            @Size(max = 255, message = "준비/실행 항목 제목은 255자 이하여야 합니다.")
            String title,

            @Schema(description = "항목 유형", example = "TIMED_ACTION")
            @NotNull(message = "항목 유형은 필수입니다.")
            ItemType itemType,

            @Schema(description = "캘린더 표시 날짜", example = "2026-07-03")
            LocalDate displayDate,

            @Schema(description = "캘린더 표시 일시", example = "2026-07-03T09:00:00")
            LocalDateTime displayTime,

            @Schema(description = "부모 일정 기준 상대 일수", example = "-7")
            Integer offsetDays,

            @Schema(description = "항목 생성 주체", example = "SYSTEM")
            @NotNull(message = "항목 생성 주체는 필수입니다.")
            CreatedBy createdBy,

            @Schema(
                    description = "추천 원본 템플릿 ID. 직접 추가한 항목은 null입니다.",
                    example = "gift_prepare_001"
            )
            @Size(max = 100, message = "추천 원본 템플릿 ID는 100자 이하여야 합니다.")
            String sourceTemplateId

    ) {
    }

    /**
     * E101~E104 과정에서 발생한 사용자 행동을 기록하기 위한 피드백 로그입니다.
     */
    @Schema(description = "추천 항목 피드백 로그")
    public record Feedback(

            @Schema(
                    description = "추천 원본 템플릿 ID. 직접 추가한 항목은 null입니다.",
                    example = "gift_prepare_001"
            )
            @Size(max = 100, message = "추천 원본 템플릿 ID는 100자 이하여야 합니다.")
            String sourceTemplateId,

            @Schema(description = "사용자 행동 유형", example = "SELECTED")
            @NotNull(message = "피드백 행동 유형은 필수입니다.")
            ActionType actionType,

            @Schema(description = "원본 추천 제목", example = "선물 준비하기")
            @Size(max = 255, message = "원본 추천 제목은 255자 이하여야 합니다.")
            String originalTitle,

            @Schema(description = "수정 후 제목", example = "꽃다발 문구 수정하기")
            @Size(max = 255, message = "수정 후 제목은 255자 이하여야 합니다.")
            String editedTitle,

            @Schema(description = "삭제 또는 제외 사유", example = "필요하지 않은 항목입니다.")
            @Size(max = 255, message = "삭제 또는 제외 사유는 255자 이하여야 합니다.")
            String reason

    ) {
    }
}
