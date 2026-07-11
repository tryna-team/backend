package com.tryna.domain.event.controller;

import com.tryna.domain.event.dto.CalendarMonthlyResponse;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendars")
public class CalendarController {

    private final EventQueryService eventQueryService;

    @GetMapping("/monthly")
    public ApiResponse<CalendarMonthlyResponse> getMonthlyCalendar(
            Authentication authentication,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        Long userId = extractUserId(authentication);
        CalendarMonthlyResponse response = eventQueryService.getMonthlyCalendar(userId, year, month);
        return ApiResponse.success(
                "B102_CALENDAR_MONTHLY_200",
                "월간 캘린더 조회에 성공했습니다.",
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
