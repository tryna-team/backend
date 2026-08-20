package com.tryna.domain.action.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Schema(description = "F104-2 월간 캘린더 내 시간형 실행 항목 조회 응답 DTO")
public record MonthlyTimedActionItemResponse(

        @Schema(description = "조회 연도", example = "2026")
        Integer year,

        @Schema(description = "조회 월", example = "8")
        Integer month,

        @Schema(description = "날짜별 시간형 실행 항목 목록")
        List<Day> days

) {

    /**
     * 월간 시간형 항목을 날짜별로 묶고 항목이 없는 날짜도 빈 목록으로 구성합니다.
     */
    public static MonthlyTimedActionItemResponse of(
            YearMonth yearMonth,
            List<TimedActionItemResponse.Item> items
    ) {
        Map<LocalDate, List<TimedActionItemResponse.Item>> itemsByDate = items.stream()
                .collect(Collectors.groupingBy(TimedActionItemResponse.Item::displayDate));

        List<Day> days = yearMonth.atDay(1)
                .datesUntil(yearMonth.atEndOfMonth().plusDays(1))
                .map(date -> new Day(date, itemsByDate.getOrDefault(date, List.of())))
                .toList();

        return new MonthlyTimedActionItemResponse(
                yearMonth.getYear(),
                yearMonth.getMonthValue(),
                days
        );
    }

    @Schema(description = "날짜별 시간형 실행 항목")
    public record Day(

            @Schema(description = "캘린더 날짜", example = "2026-08-20")
            LocalDate date,

            @Schema(description = "해당 날짜에 표시할 시간형 실행 항목 목록")
            List<TimedActionItemResponse.Item> items

    ) {
    }
}
