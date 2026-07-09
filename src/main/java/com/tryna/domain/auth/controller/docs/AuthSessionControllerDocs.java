package com.tryna.domain.auth.controller.docs;

import com.tryna.domain.auth.dto.PermissionCheckResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Auth Sessions", description = "인증 세션 관리 API")
public interface AuthSessionControllerDocs {

    @Operation(
            summary = "A104 로그인 필요 안내",
            description = "사용자가 시도하려는 기능에 대해 로그인 필요 여부와 안내 메시지를 반환합니다.",
            operationId = "checkPermission"
    )
    ResponseEntity<ApiResponse<PermissionCheckResponse>> checkPermission(
            @Parameter(description = "사용자가 시도하려는 기능 식별자 (예: EXTERNAL_CALENDAR_SYNC)", required = true)
            @RequestParam("actionType") String actionType
    );
}
