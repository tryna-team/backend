package com.tryna.domain.event.dto;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventSearchResultType;
import com.tryna.domain.label.enums.LabelColor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Schema(description = "B107 키워드 검색 응답 DTO")
public record EventSearchResponse(

        @Schema(description = "앞뒤 공백이 제거된 검색 키워드", example = "여행")
        String keyword,

        @Schema(description = "일정 및 준비/실행 항목 검색 결과")
        List<Result> results

) {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 검색 키워드와 검색 결과 목록을 B107 응답 DTO로 생성
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
     * 일정 또는 준비/실행 항목 하나의 검색 결과를 표현
     */
    @Schema(description = "키워드 검색 결과")
    public record Result(

            @Schema(
                    description = "검색 결과 유형",
                    example = "EVENT",
                    allowableValues = {"EVENT", "ACTION_ITEM"}
            )
            EventSearchResultType type,

            @Schema(
                    description = "일정 ID. ACTION_ITEM 유형에서는 부모 일정 ID",
                    example = "3"
            )
            Long eventId,

            @Schema(
                    description = "준비/실행 항목 ID. EVENT 유형에서는 null",
                    example = "31",
                    nullable = true
            )
            Long actionItemId,

            @Schema(
                    description = "검색 결과 일정 또는 부모 일정에 연결된 라벨 ID",
                    nullable = true
            )
            LabelInfo label,

            @Schema(
                    description = "검색된 일정 또는 준비/실행 항목 제목",
                    example = "일본 여행"
            )
            String title,

            @Schema(
                    description = "부모 일정 제목. EVENT 유형에서는 null",
                    example = "일본 여행",
                    nullable = true
            )
            String parentEventTitle,

            @Schema(
                    description = "일정 날짜 또는 부모 일정 날짜",
                    example = "2026-07-25",
                    nullable = true
            )
            LocalDate date,

            @Schema(
                    description = "일정 시작 시간 또는 부모 일정 시작 시간",
                    example = "14:00",
                    nullable = true
            )
            String startTime,

            @Schema(
                    description = "일정 종료 시간 또는 부모 일정 종료 시간",
                    example = "15:30",
                    nullable = true
            )
                    String endTime,

            @Schema(
                    description = "일정 장소 또는 부모 일정 장소",
                    example = "스타벅스 여의도점",
                    nullable = true
            )
            String location

    ) {

        /**
         * 일정 제목이 검색어와 일치한 결과를 생성
         *
         * @param event 검색된 일정 엔티티
         * @return EVENT 유형 검색 결과
         */
        public static Result fromEvent(Events event, LabelInfo label) {
            return new Result(
                    EventSearchResultType.EVENT,
                    event.getEventId(),
                    null,
                    label,
                    event.getTitle(),
                    null,
                    event.getStartDate(),
                    formatTime(event.getStartDatetime()),
                    formatTime(event.getEndDatetime()),
                    event.getLocation()
            );
        }

        /**
         * 준비/실행 항목 제목이 검색어와 일치한 결과를 생성
         *
         * @param event 부모 일정 엔티티
         * @param actionItem 검색된 준비/실행 항목 엔티티
         * @return ACTION_ITEM 유형 검색 결과
         */
        public static Result fromActionItem(
                Events event,
                ActionItems actionItem,
                LabelInfo label
        ) {
            return new Result(
                    EventSearchResultType.ACTION_ITEM,
                    event.getEventId(),
                    actionItem.getActionItemId(),
                    label,
                    actionItem.getTitle(),
                    event.getTitle(),
                    event.getStartDate(),
                    formatTime(event.getStartDatetime()),
                    formatTime(event.getEndDatetime()),
                    event.getLocation()
            );
        }
    }

    /**
     * 검색 결과 일정에 연결된 라벨 정보를 표현
     */
    @Schema(description = "검색 결과 일정의 라벨 정보")
    public record LabelInfo(

            @Schema(description = "라벨 ID", example = "5")
            Long labelId,

            @Schema(description = "라벨 이름", example = "개인")
            String name,

            @Schema(description = "라벨 색상", example = "GREEN")
            LabelColor color

    ) {
    }

    private static String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        LocalTime time = dateTime.toLocalTime();
        return time.format(TIME_FORMATTER);
    }
}
