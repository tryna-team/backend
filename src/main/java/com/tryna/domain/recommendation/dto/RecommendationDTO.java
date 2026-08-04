package com.tryna.domain.recommendation.dto;

import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.event.enums.DateSource;
import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.recommendation.enums.SuggestionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class RecommendationDTO {

    public record RecommendationReqDTO(
            @NotBlank(message = "임시 이벤트 ID는 필수입니다.")
            String tempEventId,
            @NotNull(message = "초안 리비전은 필수입니다.")
            Integer draftRevision,
            @NotBlank(message = "이벤트 제목은 필수입니다.")
            String eventTitle,
            @NotNull(message = "소스 유형은 필수입니다.")
            SourceType sourceType,
            @NotNull(message = "시작일 후보는 필수입니다.")
            LocalDate startDateCandidate,
            LocalTime startTimeCandidate,
            LocalDate endDateCandidate,
            LocalTime endTimeCandidate,
            DateSource startDateSource,
            String placeCandidate,
            String description,
            List<String> embeddingWords
    ) {}

    @Builder
    public record RecommendationResDTO(
            String tempEventId,
            Integer draftRevision,
            SuggestionStatus suggestionStatus,
            List<SuggestionItem> suggestions,
            String errorCode,
            List<String> errors
    ) {}

    @Builder
    public record SuggestionItem(
            String sourceCode,
            String displayText,
            ItemType itemType,
            Integer offsetDays,
            LocalDate displayDate,
            String actionType,
            String targetType,
            String defaultTiming,
            Integer selectionRank,
            String parentTempEventId
    ) {}
}
