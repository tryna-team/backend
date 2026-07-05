package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    AUTH_401_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN_EXPIRED", "토큰이 만료되었습니다."),
    AUTH_401_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    AUTH_401_INVALID_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_401_INVALID_TOKEN_TYPE", "토큰 타입이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus httpStatus, String code, String message) {
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
