package com.tryna.domain.event.controller;

import com.tryna.domain.event.dto.CalendarMonthlyResponse;
import com.tryna.domain.event.enums.EventErrorCode;
import com.tryna.domain.event.service.EventQueryService;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.response.ApiResponse;
import com.tryna.global.security.jwt.JwtTokenProvider;
import com.tryna.global.security.jwt.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendars")
public class CalendarController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final EventQueryService eventQueryService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/monthly")
    public ApiResponse<CalendarMonthlyResponse> getMonthlyCalendar(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        Long userId = extractUserId(authorizationHeader);
        CalendarMonthlyResponse response = eventQueryService.getMonthlyCalendar(userId, year, month);
        return ApiResponse.success(
                "B102_CALENDAR_MONTHLY_200",
                "월간 캘린더 조회에 성공했습니다.",
                response
        );
    }

    private Long extractUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(EventErrorCode.AUTH_401);
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(EventErrorCode.AUTH_401);
        }

        jwtTokenProvider.validateToken(token, TokenType.ACCESS);
        return jwtTokenProvider.getUserId(token);
    }
}
