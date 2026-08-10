package com.tryna.domain.alarm.controller.docs;

import com.tryna.domain.alarm.dto.ActionItemReminderResponse;
import com.tryna.domain.alarm.dto.AlarmCorrectionResponse;
import com.tryna.domain.alarm.dto.AlarmPushTokenRequest;
import com.tryna.domain.alarm.dto.AlarmStateResponse;
import com.tryna.domain.alarm.dto.EventReminderResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(
            summary = "F100 알람 접근 권한 활성화/비활성화",
            description = """
                    사용자의 알람 발송 상태(alarm_state)를 토글합니다.
                    비활성(false)에서 활성(true)으로 전환하려면 ALARM 약관 동의 이력이 필요합니다.
                    """,
            operationId = "toggleAlarmState"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<AlarmStateResponse>> toggleAlarmState(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    );

    @Operation(
            summary = "F101 일정 리마인드 알람 생성",
            description = """
                    일정 최종 저장 직후 호출되어 해당 일정의 리마인드 푸시 알람을 예약합니다.
                    일정 시작 하루 전(종일 일정은 당일 오전 7시)에 발송되도록 Redis 지연 큐에 스케줄링합니다.
                    """,
            operationId = "createEventReminder"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<EventReminderResponse>> createEventReminder(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "알람을 예약할 일정 ID") @PathVariable Long eventId
    );

    @Operation(
            summary = "F102 준비/실행 항목 리마인드 알람 생성",
            description = """
                    일정 최종 저장 직후 호출되어 준비/실행 항목의 리마인드 푸시 알람을 예약합니다.
                    UNTIMED_PREP은 부모 일정 시작 2시간 전, TIMED_ACTION은 displayTime(없으면 표시일 오전 7시)에 발송됩니다.
                    """,
            operationId = "createActionItemReminder"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<ActionItemReminderResponse>> createActionItemReminder(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "알람을 예약할 준비/실행 항목 ID") @PathVariable Long actionItemId
    );

    @Operation(
            summary = "F100 일정 알람 수정",
            description = """
                    일정 수정(C107) 직후 호출되어, 이미 반영된 최신 일정/준비·실행 항목 정보를 기준으로
                    활성화된 리마인더들의 발송 시각·제목·본문을 다시 계산하고 Redis 지연 큐도 함께 갱신합니다.
                    """,
            operationId = "correctEventAlarms"
    )
    @SecurityRequirement(name = "bearerAuth")
    ResponseEntity<ApiResponse<AlarmCorrectionResponse>> correctEventAlarms(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "알람을 보정할 일정 ID") @PathVariable Long eventId
    );
}
