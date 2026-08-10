package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum AlarmErrorCode implements ErrorCode {

    F100_ALARM_TERM_400(
            HttpStatus.BAD_REQUEST,
            "F100_ALARM_TERM_400",
            "알람 약관 동의를 거절했습니다."
    ),

    F100_PUSH_TOKEN_400(
            HttpStatus.BAD_REQUEST,
            "F100_PUSH_TOKEN_400",
            "유효하지 않은 푸시 토큰입니다."
    ),

    F100_PUSH_TOKEN_409(
            HttpStatus.CONFLICT,
            "F100_PUSH_TOKEN_409",
            "이미 푸시 토큰이 있는 사용자 입니다."
    ),

    F100_PUSH_TOKEN_500(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "F100_PUSH_TOKEN_500",
            "푸시 토큰 발급 중 서버 에러가 발생하였습니다."
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
