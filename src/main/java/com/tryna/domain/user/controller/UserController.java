package com.tryna.domain.user.controller;

import com.tryna.domain.auth.dto.AuthSessionCreateRequest;
import com.tryna.domain.auth.service.AuthService;
import com.tryna.domain.user.controller.docs.UserControllerDocs;
import com.tryna.domain.user.dto.*;
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

    @GetMapping("/me")
    @Override
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile() {
        Long userId = extractUserIdFromSecurityContext();

        // 비로그인 상태 접근 방어
        if (userId == null) {
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.AUTH_401);
        }

        UserProfileResponse response = userService.getUserProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.success("G103_USER_PROFILE_200", "계정 정보 조회에 성공했습니다.", response)
        );
    }

    @DeleteMapping("/me")
    @Override
    public ResponseEntity<ApiResponse<Void>> withdraw() {
        Long userId = extractUserIdFromSecurityContext();

        // 비로그인 상태 접근 방어
        if (userId == null) {
            throw new com.tryna.global.exception.BusinessException(com.tryna.global.exception.AuthErrorCode.AUTH_401);
        }

        userService.withdraw(userId);

        // 명세서 요구사항에 맞게 data: null 반환
        return ResponseEntity.ok(
                ApiResponse.success("G104_USER_DELETE_200", "계정 및 데이터 삭제에 성공했습니다.", null)
        );
    }

    @PatchMapping("/me/recommendation-feedback-setting")
    @Override
    public ResponseEntity<ApiResponse<FeedBackDataSettingResponse>> feedBackDataSetting(
            @Valid @RequestBody FeedBackDataSettingRequest request) {
        Long userId = extractUserIdFromSecurityContext();
        FeedBackDataSettingResponse response = userService.feedBackDataSetting(userId, request);

        return ResponseEntity.ok(
                ApiResponse.success("G105_FEEDBACK_DATA_SETTING_200",
                        "추천 피드백 데이터 사용 설정이 변경되었습니다.", response)
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
