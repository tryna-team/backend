package com.tryna.domain.auth.controller;

import com.tryna.domain.auth.controller.docs.AuthSessionControllerDocs;
import com.tryna.domain.auth.dto.AuthSessionCreateRequest;
import com.tryna.domain.auth.dto.AuthSessionResponse;
import com.tryna.domain.auth.dto.PermissionCheckResponse;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}
