package com.tryna.domain.reminder.controller;

import com.tryna.domain.reminder.controller.docs.AlarmControllerDocs;
import com.tryna.domain.reminder.dto.ActionItemReminderResponse;
import com.tryna.domain.reminder.dto.AlarmCorrectionResponse;
import com.tryna.domain.reminder.dto.AlarmDetailResponse;
import com.tryna.domain.reminder.dto.AlarmListResponse;
import com.tryna.domain.reminder.dto.AlarmPushTokenRequest;
import com.tryna.domain.reminder.dto.AlarmStateResponse;
import com.tryna.domain.reminder.dto.EventReminderResponse;
import com.tryna.domain.reminder.service.AlarmPushTokenService;
import com.tryna.domain.reminder.service.AlarmQueryService;
import com.tryna.domain.reminder.service.AlarmReminderScheduleService;
import com.tryna.domain.reminder.service.AlarmStateService;
import com.tryna.domain.reminder.service.AlarmTermService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController implements AlarmControllerDocs {

    private final AlarmTermService alarmTermService;
    private final AlarmPushTokenService alarmPushTokenService;
    private final AlarmStateService alarmStateService;
    private final AlarmQueryService alarmQueryService;
    private final AlarmReminderScheduleService alarmReminderScheduleService;

    @PostMapping("/term")
    @Override
    public ResponseEntity<ApiResponse<Void>> agreeAlarmTerm() {
        boolean newlyAgreed = alarmTermService.agreeAlarmTerm(resolveOptionalUserId());

        if (newlyAgreed) {
            return ResponseEntity.ok(
                    ApiResponse.success("F100_ALARM_TERM_200", "알람 약관 동의에 성공했습니다.", null)
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success("F100_ALARM_TERM_200", "이미 알람 약관에 동의되어 있습니다.", null)
        );
    }

    @PostMapping("/push-token")
    @Override
    public ResponseEntity<ApiResponse<Void>> registerPushToken(
            @Valid @RequestBody AlarmPushTokenRequest request
    ) {
        alarmPushTokenService.registerPushToken(resolveOptionalUserId(), request.fcmPushToken());

        return ResponseEntity.ok(
                ApiResponse.success("F100_PUSH_TOKEN_200", "푸시 토큰 발급에 성공했습니다.", null)
        );
    }

    @PatchMapping("/toggle")
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
                ApiResponse.success("F100_ALARM_TOOGLE_200", message, response)
        );
    }

    @PatchMapping("/toggle/mvp")
    @Override
    public ResponseEntity<ApiResponse<AlarmStateResponse>> toggleAlarmStateMvp(
            @AuthenticationPrincipal Long userId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        AlarmStateResponse response = alarmStateService.toggleAlarmStateMvp(userId);
        String message = response.alarmState()
                ? "알람 활성화에 성공했습니다."
                : "알람 비활성화에 성공했습니다.";

        return ResponseEntity.ok(
                ApiResponse.success("F100_ALARM_TOOGLE_MVP_200", message, response)
        );
    }

    @GetMapping("/status")
    @Override
    public ResponseEntity<ApiResponse<AlarmStateResponse>> getAlarmStatus(
            @AuthenticationPrincipal Long userId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        AlarmStateResponse response = alarmStateService.getAlarmStatus(userId);

        return ResponseEntity.ok(
                ApiResponse.success("F100_ALARM_STATUS_200", "알람 현재 상태 조회에 성공했습니다.", response)
        );
    }

    @GetMapping("/my")
    @Override
    public ResponseEntity<ApiResponse<AlarmListResponse>> getMyAlarms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        AlarmListResponse response = alarmQueryService.getMyAlarms(userId, size, cursor);

        return ResponseEntity.ok(
                ApiResponse.success("F100_ALARMLIST_200", "알람 목록 조회에 성공했습니다.", response)
        );
    }

    @GetMapping("/{reminderId}")
    @Override
    public ResponseEntity<ApiResponse<AlarmDetailResponse>> getAlarm(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reminderId
    ) {
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        AlarmDetailResponse response = alarmQueryService.getAlarm(userId, reminderId);

        return ResponseEntity.ok(
                ApiResponse.success("F100_ALARM_200", "알람 단건 조회에 성공했습니다.", response)
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
                ApiResponse.success("F101_EVENT_ALARM_200", "일정 리마인드 알람 전송에 성공했습니다.", response)
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
                ApiResponse.success("F102_ACTIONITEM_ALARM_200", "준비/실행 항목 리마인드 알람 예약에 성공했습니다.", response)
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

    private Long resolveOptionalUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
