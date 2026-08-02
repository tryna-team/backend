package com.tryna.domain.external.controller.docs;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.external.dto.ExternalCalendarStatusResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "External Calendar", description = "외부 캘린더 연동 및 조회 API")
public interface ExternalCalendarControllerDocs {

    @Operation(
            summary = "B105 외부 캘린더 일정 조회 및 표시",
            description = "사용자가 연결한 외부 캘린더의 일정을 tryna 캘린더 화면에 표시하기 위해 일정을 조회합니다.",
            operationId = "syncCalendar"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> syncCalendar(Long userId);

    @Operation(
            summary = "G102 외부 캘린더 연동 상태 조회",
            description = "사용자의 구글 캘린더 연동 상태 및 기본 정보를 조회합니다.",
            operationId = "getCalendarStatus"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<ExternalCalendarStatusResponse>> getCalendarStatus(Long userId);

    @Operation(
            summary = "G102 외부 캘린더 연동 해제",
            description = "구글 캘린더 연동을 해제하고 관련 연결 정보를 삭제합니다.",
            operationId = "disconnectCalendar"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> disconnectCalendar(
            @Parameter(hidden = true) Long userId,
            @PathVariable("provider") @Parameter(description = "해제할 소셜 제공자 (예: GOOGLE, APPLE)") Provider provider // <--- 이 부분 추가!
    );
}