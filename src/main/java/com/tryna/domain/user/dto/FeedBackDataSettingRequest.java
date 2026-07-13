package com.tryna.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "피드백 데이터 사용 설정 요청 DTO")
public record FeedBackDataSettingRequest(
        @Schema(description = "피드백 데이터 사용 동의 여부", example = "true")
        @NotNull
        Boolean isFeedbackDataCollected
) {
}
