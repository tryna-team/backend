package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    AUTH_401(HttpStatus.UNAUTHORIZED, "AUTH_401", "인증이 필요합니다."),
    AUTH_409(HttpStatus.CONFLICT, "AUTH_409", "이미 가입된 소셜 계정입니다."),
    A104_PERMISSION_CHECK_400(HttpStatus.BAD_REQUEST, "A104_PERMISSION_CHECK_400", "로그인 필요 여부 확인 요청이 올바르지 않습니다."),
    A105_AUTH_SESSION_400(HttpStatus.BAD_REQUEST, "A105_AUTH_SESSION_400", "소셜 로그인 요청이 올바르지 않습니다."),
    A106_USER_CONVERSION_400(HttpStatus.BAD_REQUEST, "A106_USER_CONVERSION_400", "회원 전환 요청이 올바르지 않습니다."),
    A106_USER_CONVERSION_403(HttpStatus.FORBIDDEN, "A106_USER_CONVERSION_403", "회원 전환 권한이 없습니다."),
    A108_AUTH_REFRESH_400(HttpStatus.BAD_REQUEST, "A108_AUTH_REFRESH_400", "잘못된 토큰 갱신 요청입니다."),
    A108_AUTH_REFRESH_401(HttpStatus.UNAUTHORIZED, "A108_AUTH_REFRESH_401", "리프레시 토큰이 만료되었거나 유효하지 않습니다. 다시 로그인해 주세요."),
    A109_AUTH_LOGOUT_400(HttpStatus.BAD_REQUEST, "A109_AUTH_LOGOUT_400", "잘못된 로그아웃 요청입니다."),
    A109_AUTH_LOGOUT_401(HttpStatus.UNAUTHORIZED, "A109_AUTH_LOGOUT_401", "로그아웃 처리를 위한 인증 정보가 유효하지 않습니다."),

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
