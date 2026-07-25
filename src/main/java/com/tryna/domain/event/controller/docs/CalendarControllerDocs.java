package com.tryna.domain.event.controller.docs;

import com.tryna.domain.event.dto.CalendarDateEventsResponse;
import com.tryna.domain.event.dto.CalendarMainResponse;
import com.tryna.domain.event.dto.CalendarMonthlyResponse;
import com.tryna.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Calendars", description = "캘린더 조회 API")
public interface CalendarControllerDocs {

    @Operation(
            summary = "B101 캘린더 메인 화면 조회",
            description = "월간 캘린더 데이터와 선택 날짜의 일정 목록을 함께 조회합니다.",
            operationId = "getCalendarMain"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<CalendarMainResponse> getCalendarMain(
            Authentication authentication,

            @Parameter(description = "조회할 연도", required = true, example = "2026")
            @RequestParam Integer year,

            @Parameter(description = "조회할 월", required = true, example = "7")
            @RequestParam Integer month,

            @Parameter(description = "선택한 날짜(yyyy-MM-dd)", required = true, example = "2026-07-17")
            @RequestParam String selectedDate
    );

    @Operation(
            summary = "B102 월간 캘린더 조회",
            description = "선택한 연월의 날짜별 일정 개수를 조회합니다.",
            operationId = "getMonthlyCalendar"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<CalendarMonthlyResponse> getMonthlyCalendar(
            Authentication authentication,

            @Parameter(description = "조회할 연도", required = true, example = "2026")
            @RequestParam Integer year,

            @Parameter(description = "조회할 월", required = true, example = "7")
            @RequestParam Integer month
    );

    @Operation(
            summary = "B103 날짜별 일정 목록 조회",
            description = "선택한 날짜에 표시할 일정 목록을 조회합니다.",
            operationId = "getDateEvents"
    )
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<CalendarDateEventsResponse> getDateEvents(
            Authentication authentication,

            @Parameter(description = "조회할 날짜(yyyy-MM-dd)", required = true, example = "2026-07-17")
            @PathVariable String date
    );
}
