package com.tryna.domain.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record EventParseRequest(
        @Schema(
                description = "C103 미리보기/추천 흐름에서 사용하는 임시 일정 ID. 최초 요청에서는 null 또는 생략하고, 이후 같은 작성 흐름에서는 기존 값을 전달합니다.",
                example = "tmp_75c44199-81f2-4761-b47d-887976267bce"
        )
        String tempEventId,

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