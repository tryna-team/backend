package com.tryna.domain.user.controller.docs;

import com.tryna.domain.user.dto.UserStatusResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Users", description = "사용자 상태 및 계정 관리 API")
public interface UserControllerDocs {

    @Operation(
            summary = "A101 앱 진입",
            description = "앱 진입 시 사용자 로그인 및 비회원 상태를 확인합니다. 기기에 토큰이 존재할 경우 헤더에 포함하여 요청합니다.",
            operationId = "getUserStatus"
    )
    ResponseEntity<ApiResponse<UserStatusResponse>> getUserStatus();
}
