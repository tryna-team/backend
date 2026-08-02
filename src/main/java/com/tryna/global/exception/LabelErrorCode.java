package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum LabelErrorCode implements ErrorCode {

    /**
     * B108-2: 라벨 생성
     */
    B108_LABEL_CREATE_400(
            HttpStatus.BAD_REQUEST,
            "B108_LABEL_CREATE_400",
            "라벨 생성 요청값이 올바르지 않습니다."
    ),

    B108_LABEL_CREATE_409(
            HttpStatus.CONFLICT,
            "B108_LABEL_CREATE_409",
            "같은 이름의 라벨이 이미 존재합니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    LabelErrorCode(
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