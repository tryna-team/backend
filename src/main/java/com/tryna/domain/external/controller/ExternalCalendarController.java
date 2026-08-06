package com.tryna.domain.external.controller;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.external.controller.docs.ExternalCalendarControllerDocs;
import com.tryna.domain.external.dto.CalendarStatusResponse;
import com.tryna.domain.external.service.CalendarSyncService;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExternalCalendarController implements ExternalCalendarControllerDocs {

    private final CalendarSyncService calendarSyncService;

    @Override
    @PostMapping("/external-events")
    public ResponseEntity<ApiResponse<Void>> syncCalendar(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "year", required = false) Integer year
    ) {
        calendarSyncService.syncGoogleCalendar(userId, year);
        return ResponseEntity.ok(ApiResponse.success("B105_EXTERNAL_EVENT_200", "외부 캘린더 동기화 및 조회에 성공했습니다.", null));
    }

    @Override
    @GetMapping("/external-calendar-connections")
    public ResponseEntity<ApiResponse<CalendarStatusResponse>> getCalendarStatus(
            @AuthenticationPrincipal Long userId
    ) {
        CalendarStatusResponse response = calendarSyncService.getUnifiedCalendarStatus(userId);
        return ResponseEntity.ok(ApiResponse.success("G102_EXTERNAL_CALENDAR_STATUS_200", "외부 캘린더 연동 상태 조회에 성공했습니다.", response));
    }

    @Override
    @DeleteMapping("/external-calendar-connections/{provider}")
    public ResponseEntity<ApiResponse<Void>> disconnectCalendar(
            @AuthenticationPrincipal Long userId,
            @PathVariable("provider") Provider provider
    ) {
        calendarSyncService.disconnectGoogleCalendar(userId, provider);
        return ResponseEntity.ok(ApiResponse.success("G102_EXTERNAL_CALENDAR_DISCONNECT_200", "외부 캘린더 연동 해제에 성공했습니다.", null));
    }
}