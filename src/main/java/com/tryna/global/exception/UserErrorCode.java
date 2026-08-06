package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {

    USER_404(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
    G103_USER_PROFILE_404(HttpStatus.NOT_FOUND, "G103_USER_PROFILE_404", "계정 정보를 찾을 수 없습니다."),
    A102_GUEST_CREATE_400(HttpStatus.BAD_REQUEST, "A102_GUEST_CREATE_400", "비회원 생성 요청이 올바르지 않습니다."),
    A101_USER_STATUS_400(HttpStatus.BAD_REQUEST, "A101_USER_STATUS_400", "잘못된 앱 진입 상태 조회 요청입니다.");

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
