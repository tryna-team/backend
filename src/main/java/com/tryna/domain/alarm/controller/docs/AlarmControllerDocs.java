package com.tryna.domain.alarm.controller.docs;

import com.tryna.domain.alarm.dto.AlarmPushTokenRequest;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Alarms", description = "알람 약관 및 발송 설정 API")
public interface AlarmControllerDocs {

    @Operation(
            summary = "F100 알람 약관 동의",
            description = """
                    ALARM 약관에 동의하고 사용자의 알람 발송 상태(alarm_state)를 활성화합니다.
                    terms 테이블의 ALARM 약관에 대한 user_agreed_terms 이력을 생성합니다.
                    """,
            operationId = "agreeAlarmTerm"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> agreeAlarmTerm(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    );

    @Operation(
            summary = "F100 FCM 푸시 토큰 발급",
            description = """
                    푸시 알람 전송에 필요한 FCM 푸시 토큰을 검증하여 Redis에 저장합니다.
                    이미 등록된 푸시 토큰이 있는 사용자는 409로 거부됩니다.
                    """,
            operationId = "registerPushToken"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<Void>> registerPushToken(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AlarmPushTokenRequest request
    );
}
