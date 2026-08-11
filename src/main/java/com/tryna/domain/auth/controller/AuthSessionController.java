package com.tryna.domain.auth.controller;

import com.tryna.domain.auth.controller.docs.AuthSessionControllerDocs;
import com.tryna.domain.auth.dto.*;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth-sessions")
@RequiredArgsConstructor
public class AuthSessionController implements AuthSessionControllerDocs {

    private final AuthService authService;

    @GetMapping("/permissions")
    @Override
    public ResponseEntity<ApiResponse<PermissionCheckResponse>> checkPermission(
            @RequestParam("actionType") String actionType
    ) {

        PermissionCheckResponse response = authService.checkPermission(actionType);
        return ResponseEntity.ok(
                ApiResponse.success("A104_PERMISSION_CHECK_200", "로그인 필요 여부 확인에 성공했습니다.", response)
        );
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<AuthSessionResponse>> socialLogin(
            @Valid @RequestBody AuthSessionCreateRequest request
    ) {
        AuthSessionResponse response = authService.socialLogin(request);

        return ResponseEntity.ok(
                ApiResponse.success("A105_AUTH_SESSION_200", "소셜 로그인에 성공했습니다.", response)
        );
    }

    @PostMapping("/refresh")
    @Override
    public ResponseEntity<ApiResponse<AuthTokenResponse>> reissueToken(
            @Valid @RequestBody AuthTokenRefreshRequest request
    ) {
        // 서비스 단에서 검증 및 재발급 처리
        AuthTokenResponse response = authService.reissue(request);

        return ResponseEntity.ok(
                ApiResponse.success("A108_AUTH_REFRESH_200", "토큰 갱신에 성공했습니다.", response)
        );
    }

    @DeleteMapping("/me")
    @Override
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Long userId,
            @RequestParam("deviceId") String deviceId
    ) {
        authService.logout(userId, deviceId);
        return ResponseEntity.ok(ApiResponse.success("A109_AUTH_LOGOUT_200", "로그아웃 되었습니다.", null));
    }
}
