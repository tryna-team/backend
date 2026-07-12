package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {

    USER_404(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
    G103_USER_PROFILE_404(HttpStatus.NOT_FOUND, "G103_USER_PROFILE_404", "계정 정보를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus httpStatus, String code, String message) {
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
