package com.tryna.domain.auth.controller.docs;

import com.tryna.domain.auth.dto.*;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Auth Sessions", description = "인증 세션 관리 API")
public interface AuthSessionControllerDocs {

    @Operation(
            summary = "A104 로그인 필요 안내",
            description = "사용자가 시도하려는 기능에 대해 로그인 필요 여부와 안내 메시지를 반환합니다.",
            operationId = "checkPermission"
    )
    @SecurityRequirements({})
    ResponseEntity<ApiResponse<PermissionCheckResponse>> checkPermission(
            @Parameter(description = "사용자가 시도하려는 기능 식별자 (예: EXTERNAL_CALENDAR_SYNC)", required = true)
            @RequestParam("actionType") String actionType
    );

    @Operation(
            summary = "A105 소셜 회원가입 및 로그인",
            description = "소셜 서버 토큰을 검증하여 기존 회원은 로그인을, 신규 회원은 약관 동의와 함께 가입을 처리합니다.",
            operationId = "socialLogin"
    )
    @SecurityRequirements({})
    ResponseEntity<ApiResponse<AuthSessionResponse>> socialLogin(
            @Valid @RequestBody AuthSessionCreateRequest request
    );

    @Operation(
            summary = "A108 토큰 갱신",
            description = "만료된 액세스 토큰을 재발급받기 위해 리프레시 토큰을 검증하고 새로운 토큰 쌍을 반환합니다. (Refresh Token Rotation 정책 적용)",
            operationId = "reissueToken"
    )
    ResponseEntity<ApiResponse<AuthTokenResponse>> reissueToken(
            @Valid @RequestBody AuthTokenRefreshRequest request
    );

    @Operation(
            summary = "A109 로그아웃",
            description = "현재 기기의 세션을 만료시키고 푸시 알림(FCM) 토큰을 제거하여 로그아웃 처리합니다.",
            operationId = "logout"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(description = "접속 기기 식별자", required = true)
            @RequestParam("deviceId") String deviceId
    );
}
