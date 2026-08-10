package com.tryna.domain.alarm.controller;

import com.tryna.domain.alarm.controller.docs.AlarmControllerDocs;
import com.tryna.domain.alarm.dto.ActionItemReminderResponse;
import com.tryna.domain.alarm.dto.AlarmCorrectionResponse;
import com.tryna.domain.alarm.dto.AlarmPushTokenRequest;
import com.tryna.domain.alarm.dto.AlarmStateResponse;
import com.tryna.domain.alarm.dto.EventReminderResponse;
import com.tryna.domain.alarm.service.AlarmPushTokenService;
import com.tryna.domain.alarm.service.AlarmStateService;
import com.tryna.domain.alarm.service.AlarmTermService;
import com.tryna.domain.reminder.service.AlarmReminderScheduleService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController implements AlarmControllerDocs {

    private final AlarmTermService alarmTermService;
    private final AlarmPushTokenService alarmPushTokenService;
    private final AlarmStateService alarmStateService;
    private final AlarmReminderScheduleService alarmReminderScheduleService;

    @PostMapping("/term")
    @Override
    public ResponseEntity<ApiResponse<Void>> agreeAlarmTerm(
            @AuthenticationPrincipal Long userId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        alarmTermService.agreeAlarmTerm(userId);

        return ResponseEntity.ok(
                ApiResponse.success("F100_ALARM_TERM_200", "알람 약관 동의에 성공했습니다.", null)
        );
    }

    @PostMapping("/push-token")
    @Override
    public ResponseEntity<ApiResponse<Void>> registerPushToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AlarmPushTokenRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        alarmPushTokenService.registerPushToken(userId, request.fcmPushToken());

        return ResponseEntity.ok(
                ApiResponse.success("F100_PUSH_TOKEN_200", "푸시 토큰 발급에 성공했습니다.", null)
        );
    }

    @PatchMapping("/state")
    @Override
    public ResponseEntity<ApiResponse<AlarmStateResponse>> toggleAlarmState(
            @AuthenticationPrincipal Long userId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        AlarmStateResponse response = alarmStateService.toggleAlarmState(userId);
        String message = response.alarmState()
                ? "알람 활성화에 성공했습니다."
                : "알람 비활성화에 성공했습니다.";

        return ResponseEntity.ok(
                ApiResponse.success("F100_ALARM_STATE_200", message, response)
        );
    }

    @PostMapping("/remind/event/{eventId}")
    @Override
    public ResponseEntity<ApiResponse<EventReminderResponse>> createEventReminder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        EventReminderResponse response = alarmReminderScheduleService.createEventReminder(userId, eventId);

        return ResponseEntity.ok(
                ApiResponse.success("F101_EVEMT_ALARM_200", "일정 리마인드 알람 전송에 성공했습니다.", response)
        );
    }

    @PostMapping("/remind/action-item/{actionItemId}")
    @Override
    public ResponseEntity<ApiResponse<ActionItemReminderResponse>> createActionItemReminder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long actionItemId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        ActionItemReminderResponse response = alarmReminderScheduleService.createActionItemReminder(userId, actionItemId);

        return ResponseEntity.ok(
                ApiResponse.success("F102_ACTIONITEM_ALARM_200", "일정 리마인드 알람 전송에 성공했습니다.", response)
        );
    }

    @PatchMapping("/{eventId}")
    @Override
    public ResponseEntity<ApiResponse<AlarmCorrectionResponse>> correctEventAlarms(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        AlarmCorrectionResponse response = alarmReminderScheduleService.correctEventAlarms(userId, eventId);

        return ResponseEntity.ok(
                ApiResponse.success("F100_CORRECT_ALARM_200", "알람 수정에 성공했습니다.", response)
        );
    }
}
