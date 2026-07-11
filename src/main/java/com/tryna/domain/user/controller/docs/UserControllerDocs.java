package com.tryna.domain.user.controller.docs;

import com.tryna.domain.auth.dto.AuthSessionCreateRequest;
import com.tryna.domain.user.dto.UserConversionResponse;
import com.tryna.domain.user.dto.UserProfileResponse;
import com.tryna.domain.user.dto.UserStatusResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Users", description = "사용자 상태 및 계정 관리 API")
public interface UserControllerDocs {

    @Operation(
            summary = "A101 앱 진입",
            description = "앱 진입 시 사용자 로그인 및 비회원 상태를 확인합니다. 기기에 토큰이 존재할 경우 헤더에 포함하여 요청합니다.",
            operationId = "getUserStatus"
    )
    ResponseEntity<ApiResponse<UserStatusResponse>> getUserStatus();

    @Operation(
            summary = "A106 회원 전환 유도",
            description = "비회원(GUEST) 상태의 사용자를 정식 회원(USER)으로 전환하고 소셜 계정을 연동합니다.",
            operationId = "convertGuestToUser"
    )
    ResponseEntity<ApiResponse<UserConversionResponse>> convertGuestToUser(
            @Valid @RequestBody AuthSessionCreateRequest request
    );

    @Operation(
            summary = "G103 계정 정보 확인",
            description = "현재 로그인한 사용자의 프로필 및 소셜 연동 여부, 외부 캘린더 연동 여부를 조회합니다.",
            operationId = "getUserProfile"
    )
    ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile();

    @Operation(
            summary = "G104 데이터 삭제 (회원 탈퇴)",
            description = "현재 사용자의 모든 데이터를 삭제(Hard/Soft)하고 회원을 탈퇴합니다.",
            operationId = "withdraw"
    )
    ResponseEntity<ApiResponse<Void>> withdraw(); // data: null을 반환하므로 제네릭은 Void
}
