package com.tryna.domain.alarm.controller.docs;

import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
}
