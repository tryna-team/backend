package com.tryna.domain.auth.controller.docs;

import com.tryna.domain.auth.dto.AuthSessionCreateRequest;
import com.tryna.domain.auth.dto.AuthSessionResponse;
import com.tryna.domain.auth.dto.PermissionCheckResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(
            summary = "A105 소셜 회원가입 및 로그인",
            description = "소셜 서버 토큰을 검증하여 기존 회원은 로그인을, 신규 회원은 약관 동의와 함께 가입을 처리합니다.",
            operationId = "socialLogin"
    )
    ResponseEntity<ApiResponse<AuthSessionResponse>> socialLogin(
            @Valid @RequestBody AuthSessionCreateRequest request
    );
}
