package com.tryna.domain.action.controller.docs;

import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.action.dto.ActionItemSaveResponse;
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
}