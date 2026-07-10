package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum ActionErrorCode implements ErrorCode {

    /**
     * E105: 준비/실행 항목 일괄 저장
     */
    E105_ACTION_ITEM_400_1(
            HttpStatus.BAD_REQUEST,
            "E105_ACTION_ITEM_400_1",
            "저장할 준비/실행 항목이 없습니다."
    ),

    E105_ACTION_ITEM_400_2(
            HttpStatus.BAD_REQUEST,
            "E105_ACTION_ITEM_400_2",
            "준비/실행 항목 요청값이 올바르지 않습니다."
    ),

    E105_ACTION_ITEM_403(
            HttpStatus.FORBIDDEN,
            "E105_ACTION_ITEM_403",
            "해당 일정에 준비/실행 항목을 저장할 권한이 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ActionErrorCode(
            HttpStatus httpStatus,
            String code,
            String message
    ) {
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