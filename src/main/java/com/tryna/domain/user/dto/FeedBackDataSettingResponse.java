package com.tryna.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "피드백 데이터 사용 설정 응답 DTO")
public record FeedBackDataSettingResponse(
        @Schema(description = "피드백 데이터 사용 동의 여부", example = "true")
        Boolean isFeedbackDataCollected
) {
}
