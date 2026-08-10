package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum RecommendationErrorCode implements ErrorCode {

    STALE_DRAFT_REVISION_409(
            HttpStatus.CONFLICT,
            "STALE_DRAFT_REVISION_409",
            "최신 일정 입력이 존재하여 이전 추천 요청을 중단했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    RecommendationErrorCode(
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
