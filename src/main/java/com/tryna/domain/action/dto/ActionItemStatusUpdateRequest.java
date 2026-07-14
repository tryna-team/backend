package com.tryna.domain.action.dto;

import com.tryna.domain.action.enums.ActionItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "E106 준비/실행 항목 상태 변경 요청 DTO")
public record ActionItemStatusUpdateRequest(

        @Schema(
                description = "변경할 준비/실행 항목 상태. 완료 처리 시 COMPLETED, 완료 취소 시 PENDING",
                example = "COMPLETED"
        )
        @NotNull(message = "변경할 준비/실행 항목 상태는 필수입니다.")
        ActionItemStatus actionItemStatus

) {
}