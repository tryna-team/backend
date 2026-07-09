package com.tryna.domain.auth.controller;

import com.tryna.domain.auth.controller.docs.AuthSessionControllerDocs;
import com.tryna.domain.auth.dto.PermissionCheckResponse;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
