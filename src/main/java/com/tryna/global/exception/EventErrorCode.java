package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum EventErrorCode implements ErrorCode {

    B101_CALENDAR_MAIN_400(HttpStatus.BAD_REQUEST, "B101_CALENDAR_MAIN_400", "잘못된 캘린더 메인 조회 요청입니다."),
    B102_CALENDAR_MONTHLY_400(HttpStatus.BAD_REQUEST, "B102_CALENDAR_MONTHLY_400", "잘못된 월간 캘린더 조회 요청입니다."),
    B103_CALENDAR_DATE_EVENTS_400(HttpStatus.BAD_REQUEST, "B103_CALENDAR_DATE_EVENTS_400", "잘못된 날짜별 일정 목록 조회 요청입니다."),
    B104_EVENT_DETAIL_400(HttpStatus.BAD_REQUEST, "B104_EVENT_DETAIL_400", "잘못된 일정 상세 조회 요청입니다."),
    B104_EVENT_DETAIL_403(HttpStatus.FORBIDDEN, "B104_EVENT_DETAIL_403", "해당 일정에 접근할 수 없습니다."),
    B104_EVENT_DETAIL_404(HttpStatus.NOT_FOUND, "B104_EVENT_DETAIL_404", "일정을 찾을 수 없습니다."),
    B107_EVENT_SEARCH_400(HttpStatus.BAD_REQUEST, "B107_EVENT_SEARCH_400", "검색어가 올바르지 않습니다."),
    C101_EVENT_INPUT_400(HttpStatus.BAD_REQUEST, "C101_EVENT_INPUT_400", "일정 문장을 입력해주세요."),
    C102_EVENT_PARSE_500(HttpStatus.INTERNAL_SERVER_ERROR, "C102_EVENT_PARSE_500", "일정 분석 중 오류가 발생했습니다."),
    C104_EVENT_SAVE_400(HttpStatus.BAD_REQUEST, "C104_EVENT_SAVE_400", "잘못된 일정 저장 요청입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    EventErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
