package com.tryna.domain.event.controller;

import com.tryna.domain.event.controller.docs.CalendarControllerDocs;
import com.tryna.domain.event.dto.CalendarDateEventsResponse;
import com.tryna.domain.event.dto.CalendarMainResponse;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.domain.external.CalendarSyncRequestedEvent;
import com.tryna.domain.external.service.CalendarSyncService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

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
    private final ApplicationEventPublisher applicationEventPublisher;

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
            applicationEventPublisher.publishEvent(new CalendarSyncRequestedEvent(userId, year));
        }

        CalendarMainResponse response = eventQueryService.getCalendarMain(userId, year, month, selectedDate);
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

        try {
            LocalDate parsed = LocalDate.parse(date);
            int year = parsed.getYear();
            boolean hasYearEvents = eventQueryService.hasEventsInYear(userId, year);
            if (!hasYearEvents) {
                applicationEventPublisher.publishEvent(new CalendarSyncRequestedEvent(userId, year)); // 🚀 [이벤트 발행]
            }
        } catch (DateTimeParseException e) {
            // 잘못된 날짜 형식은 파싱 예외로 처리 (실제 400 에러는 서비스 단에서 발생)
            log.debug("날짜별 일정 조회 중 잘못된 날짜 형식 입력: {}", date);
        } catch (Exception e) {
            // 동기화 이벤트 발행 등 기타 예외는 로그로 기록
            log.warn("날짜별 일정 조회 중 연도 동기화 트리거 실패: {}", e.getMessage(), e);
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
