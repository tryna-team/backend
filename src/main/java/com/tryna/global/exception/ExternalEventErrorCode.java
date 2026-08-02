package com.tryna.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExternalEventErrorCode implements ErrorCode {

    B105_EXTERNAL_EVENT_400(HttpStatus.BAD_REQUEST, "B105_EXTERNAL_EVENT_400", "외부 캘린더 연동 정보가 없습니다."),
    B105_EXTERNAL_EVENT_401(HttpStatus.UNAUTHORIZED, "B105_EXTERNAL_EVENT_401", "외부 캘린더 권한이 만료되었습니다. 다시 연동해 주세요."),
    B105_EXTERNAL_EVENT_500(HttpStatus.INTERNAL_SERVER_ERROR, "B105_EXTERNAL_EVENT_500", "외부 캘린더 서버와 통신 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}