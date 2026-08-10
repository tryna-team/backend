package com.tryna.domain.alarm.controller;

import com.tryna.domain.alarm.controller.docs.AlarmControllerDocs;
import com.tryna.domain.alarm.service.AlarmTermService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController implements AlarmControllerDocs {

    private final AlarmTermService alarmTermService;

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
}
