package com.tryna.domain.event.enums;

import com.tryna.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EventErrorCode implements ErrorCode {

    B102_CALENDAR_MONTHLY_400(HttpStatus.BAD_REQUEST, "B102_CALENDAR_MONTHLY_400", "잘못된 월간 캘린더 조회 요청입니다."),
    B013_CALENDAR_DATE_EVENTS_400(HttpStatus.BAD_REQUEST, "B013_CALENDAR_DATE_EVENTS_400", "잘못된 날짜별 일정 목록 조회 요청입니다."),
    B104_EVENT_DETAIL_400(HttpStatus.BAD_REQUEST, "B104_EVENT_DETAIL_400", "잘못된 일정 상세 조회 요청입니다."),
    B104_EVENT_DETAIL_403(HttpStatus.FORBIDDEN, "B104_EVENT_DETAIL_403", "해당 일정에 접근할 수 없습니다."),
    B104_EVENT_DETAIL_404(HttpStatus.NOT_FOUND, "B104_EVENT_DETAIL_404", "일정을 찾을 수 없습니다.");

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
