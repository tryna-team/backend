package com.tryna.domain.event.controller;

import com.tryna.domain.event.controller.docs.CalendarControllerDocs;
import com.tryna.domain.event.dto.CalendarDateEventsResponse;
import com.tryna.domain.event.dto.CalendarMainResponse;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendars")
public class CalendarController implements CalendarControllerDocs {

    private final EventQueryService eventQueryService;

    @Override
    @GetMapping("/main")
    public ApiResponse<CalendarMainResponse> getCalendarMain(
            Authentication authentication,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam String selectedDate
    ) {
        Long userId = extractUserId(authentication);

        CalendarMainResponse response = eventQueryService.getCalendarMainWithSyncCheck(userId, year, month, selectedDate);

        return ApiResponse.success(
                "B101_CALENDAR_MAIN_200",
                "캘린더 메인 화면 조회에 성공했습니다.",
                response
        );
    }

    @GetMapping("/dates/{date}/events")
    public ApiResponse<CalendarDateEventsResponse> getDateEvents(
            Authentication authentication,
            @PathVariable String date
    ) {
        Long userId = extractUserId(authentication);

        CalendarDateEventsResponse response = eventQueryService.getDateEventsWithSyncCheck(userId, date);

        return ApiResponse.success(
                "B103_CALENDAR_DATE_EVENTS_200",
                "날짜별 일정 목록 조회에 성공했습니다.",
                response
        );
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }

        return userId;
    }
}
