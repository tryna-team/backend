package com.tryna.domain.user.controller;

import com.tryna.domain.user.controller.docs.UserControllerDocs;
import com.tryna.domain.user.dto.UserStatusResponse;
import com.tryna.domain.user.service.UserService;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserService userService;

    @GetMapping("/status")
    @Override
    public ResponseEntity<ApiResponse<UserStatusResponse>> getUserStatus() {
        Long userId = extractUserIdFromSecurityContext();
        UserStatusResponse response = userService.getUserStatus(userId);

        return ResponseEntity.ok(
                ApiResponse.success("A101_USER_STATUS_200", "앱 진입 상태 조회에 성공했습니다.", response)
        );
    }

    // SecurityContext에서 안전하게 userId 추출 (비로그인이면 null 반환)
    private Long extractUserIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        return null;
    }
}
