package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    COMMON_400(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    AUTH_401(HttpStatus.UNAUTHORIZED, "AUTH_401", "인증이 필요합니다."),
    COMMON_403(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    COMMON_409(HttpStatus.CONFLICT, "COMMON_409", "요청이 현재 서버의 상태와 충돌합니다."),
    COMMON_500(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String code, String message) {
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
