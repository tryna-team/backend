package com.tryna.domain.event.dto;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.event.entity.Events;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Schema(description = "B107 키워드 검색 응답 DTO")
public record EventSearchResponse(

        @Schema(description = "앞뒤 공백이 제거된 검색 키워드", example = "고기")
        String keyword,

        @Schema(description = "검색된 일정 목록")
        List<Result> results

) {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 검색된 일정과 일치한 준비/실행 항목을 B107 응답 DTO로 변환합니다.
     *
     * @param keyword 정규화된 검색 키워드
     * @param results 검색 결과 목록
     * @return 키워드 검색 응답
     */
    public static EventSearchResponse of(
            String keyword,
            List<Result> results
    ) {
        return new EventSearchResponse(keyword, results);
    }

    /**
     * 검색 결과에 표시할 일정 하나를 표현합니다.
     */
    @Schema(description = "키워드 검색 결과 일정")
    public record Result(

            @Schema(description = "일정 ID", example = "1")
            Long eventId,

            @Schema(description = "일정 제목", example = "김치찜 만들기")
            String title,

            @Schema(description = "일정 장소", example = "집", nullable = true)
            String location,

            @Schema(description = "일정 시작 날짜", example = "2026-07-20", nullable = true)
            LocalDate startDate,

            @Schema(description = "일정 시작 시간", example = "18:00", nullable = true)
            String startTime,

            @Schema(description = "일정 종료 날짜", example = "2026-07-20", nullable = true)
            LocalDate endDate,

            @Schema(description = "일정 종료 시간", example = "19:30", nullable = true)
            String endTime,

            @Schema(description = "종일 일정 여부", example = "false")
            Boolean isAllDay,

            @Schema(description = "검색어와 일치한 준비/실행 항목 목록")
            List<MatchedActionItem> matchedActionItems

    ) {

        /**
         * 일정 엔티티와 검색어에 일치한 준비/실행 항목을 검색 결과로 변환합니다.
         *
         * @param event 검색된 일정 엔티티
         * @param matchedActionItems 검색어와 일치한 준비/실행 항목 목록
         * @return 일정 검색 결과
         */
        public static Result from(
                Events event,
                List<ActionItems> matchedActionItems
        ) {
            return new Result(
                    event.getEventId(),
                    event.getTitle(),
                    event.getLocation(),
                    event.getStartDate(),
                    formatTime(event.getStartDatetime()),
                    event.getEndDate(),
                    formatTime(event.getEndDatetime()),
                    event.getIsAllDay(),
                    matchedActionItems.stream()
                            .map(MatchedActionItem::from)
                            .toList()
            );
        }
    }

    /**
     * 검색어와 일치한 준비/실행 항목 하나를 표현합니다.
     */
    @Schema(description = "검색어와 일치한 준비/실행 항목")
    public record MatchedActionItem(

            @Schema(description = "준비/실행 항목 ID", example = "10")
            Long actionItemId,

            @Schema(description = "준비/실행 항목 제목", example = "고기 구매")
            String title

    ) {

        /**
         * ActionItems 엔티티를 검색 결과 하위 항목으로 변환합니다.
         *
         * @param actionItem 준비/실행 항목 엔티티
         * @return 검색어와 일치한 준비/실행 항목
         */
        private static MatchedActionItem from(ActionItems actionItem) {
            return new MatchedActionItem(
                    actionItem.getActionItemId(),
                    actionItem.getTitle()
            );
        }
    }

    private static String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        LocalTime time = dateTime.toLocalTime();
        return time.format(TIME_FORMATTER);
    }
}
