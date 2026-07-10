package com.tryna.domain.user.controller;

import com.tryna.domain.auth.dto.AuthSessionCreateRequest;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.domain.user.controller.docs.UserControllerDocs;
import com.tryna.domain.user.dto.UserConversionResponse;
import com.tryna.domain.user.dto.UserStatusResponse;
import com.tryna.domain.user.service.UserService;
import com.tryna.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/status")
    @Override
    public ResponseEntity<ApiResponse<UserStatusResponse>> getUserStatus() {
        Long userId = extractUserIdFromSecurityContext();
        UserStatusResponse response = userService.getUserStatus(userId);

        return ResponseEntity.ok(
                ApiResponse.success("A101_USER_STATUS_200", "앱 진입 상태 조회에 성공했습니다.", response)
        );
    }

    @PostMapping("/conversions")
    @Override
    public ResponseEntity<ApiResponse<UserConversionResponse>> convertGuestToUser(
            @Valid @RequestBody AuthSessionCreateRequest request
    ) {
        Long userId = extractUserIdFromSecurityContext();

        // 인증 필터에서 예외 처리가 되지만, 혹시 모를 NPE 방어
        if (userId == null) {
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.AUTH_401);
        }

        UserConversionResponse response = authService.convertGuestToUser(userId, request);

        return ResponseEntity.ok(
                ApiResponse.success("A106_USER_CONVERSION_200", "회원 전환에 성공했습니다.", response)
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
