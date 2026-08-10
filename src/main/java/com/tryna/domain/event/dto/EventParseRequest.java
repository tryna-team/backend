package com.tryna.domain.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record EventParseRequest(
        String eventTitle,

        @Schema(
                description = "프론트 디바운싱 요청 순서 식별값. 0 이상의 정수이며 응답에 그대로 반환됩니다.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "0"
        )
        @NotNull(message = "draftRevision은 필수입니다.")
        @Min(value = 0, message = "draftRevision은 0 이상이어야 합니다.")
        Integer draftRevision,

        LocalDate selectedDate
) {
}