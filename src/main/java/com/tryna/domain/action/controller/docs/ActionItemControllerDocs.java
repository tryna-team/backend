package com.tryna.domain.action.controller.docs;

import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.action.dto.ActionItemSaveResponse;
import com.tryna.domain.action.dto.ActionItemStatusUpdateRequest;
import com.tryna.domain.action.dto.ActionItemStatusUpdateResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Action Items", description = "준비/실행 항목 관리 API")
public interface ActionItemControllerDocs {

    @Operation(
            summary = "E105 제안 항목 저장",
            description = "최종 저장 후보 목록과 피드백 로그를 받아 준비/실행 항목과 추천 피드백을 일괄 저장합니다.",
            operationId = "saveActionItems"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<ActionItemSaveResponse>> saveActionItems(
            @Parameter(description = "준비/실행 항목을 저장할 일정 ID", required = true)
            @PathVariable("eventId") Long eventId,

            @Valid @RequestBody ActionItemSaveRequest request
    );

    @Operation(
            summary = "E106 준비/실행 항목 완료 처리",
            description = "준비/실행 항목을 완료 상태로 변경하거나 완료 처리를 취소하여 대기 상태로 되돌립니다.",
            operationId = "updateActionItemStatus"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<ActionItemStatusUpdateResponse>> updateActionItemStatus(
            @Parameter(
                    description = "상태를 변경할 준비/실행 항목 ID",
                    required = true
            )
            @PathVariable("actionItemId") Long actionItemId,

            @Valid
            @RequestBody
            ActionItemStatusUpdateRequest request
    );
}