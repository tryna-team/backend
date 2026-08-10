package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum AlarmErrorCode implements ErrorCode {

    F100_ALARM_TERM_400(
            HttpStatus.BAD_REQUEST,
            "F100_ALARM_TERM_400",
            "알람 약관 동의를 거절했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AlarmErrorCode(HttpStatus httpStatus, String code, String message) {
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
