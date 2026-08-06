package com.tryna.domain.event.controller;

import com.tryna.domain.event.controller.docs.CalendarControllerDocs;
import com.tryna.domain.event.dto.CalendarDateEventsResponse;
import com.tryna.domain.event.dto.CalendarMainResponse;
import com.tryna.domain.event.dto.CalendarMonthlyResponse;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.domain.external.service.CalendarSyncService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendars")
public class CalendarController implements CalendarControllerDocs {

    private final EventQueryService eventQueryService;
    private final CalendarSyncService calendarSyncService;

    @Override
    @GetMapping("/main")
    public ApiResponse<CalendarMainResponse> getCalendarMain(
            Authentication authentication,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam String selectedDate
    ) {
        Long userId = extractUserId(authentication);

        // 연도 동기화를 수행하여 selectedDate의 일정이 DB에 존재하도록 보장
        boolean hasYearEvents = eventQueryService.hasEventsInYear(userId, year);
        if (!hasYearEvents) {
            calendarSyncService.syncGoogleCalendar(userId, year);
        }

        CalendarMainResponse response = eventQueryService.getCalendarMain(userId, year, month, selectedDate);
        return ApiResponse.success(
                "B101_CALENDAR_MAIN_200",
                "캘린더 메인 화면 조회에 성공했습니다.",
                response
        );
    }

    @Override
    @GetMapping("/monthly")
    public ApiResponse<CalendarMonthlyResponse> getMonthlyCalendar(
            Authentication authentication,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        Long userId = extractUserId(authentication);

        // 연도에 이벤트가 없으면 calendarSyncService.syncGoogleCalendar(userId, year)를 동기 호출
        boolean hasYearEvents = eventQueryService.hasEventsInYear(userId, year);
        if (!hasYearEvents) {
            calendarSyncService.syncGoogleCalendar(userId, year);
        }

        CalendarMonthlyResponse response = eventQueryService.getMonthlyCalendar(userId, year, month);
        return ApiResponse.success(
                "B102_CALENDAR_MONTHLY_200",
                "월간 캘린더 조회에 성공했습니다.",
                response
        );
    }

    @Override
    @GetMapping("/dates/{date}/events")
    public ApiResponse<CalendarDateEventsResponse> getDateEvents(
            Authentication authentication,
            @PathVariable String date
    ) {
        Long userId = extractUserId(authentication);

        // 요청된 날짜에 해당하는 연도의 동기화를 보장합니다.
        try {
            LocalDate parsed = LocalDate.parse(date);
            int year = parsed.getYear();
            boolean hasYearEvents = eventQueryService.hasEventsInYear(userId, year);
            if (!hasYearEvents) {
                calendarSyncService.syncGoogleCalendar(userId, year);
            }
        } catch (Exception ignored) {
        }

        CalendarDateEventsResponse response = eventQueryService.getDateEvents(userId, date);
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