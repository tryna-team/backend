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

    /**
     * E106: 준비/실행 항목 완료 상태 변경
     */
    E106_ACTION_ITEM_400(
            HttpStatus.BAD_REQUEST,
            "E106_ACTION_ITEM_400",
            "준비/실행 항목 상태 변경 요청이 올바르지 않습니다."
    ),

    E106_ACTION_ITEM_404(
            HttpStatus.NOT_FOUND,
            "E106_ACTION_ITEM_404",
            "준비/실행 항목을 찾을 수 없습니다."
    ),

    /**
     * F103: 일정 상세 내 준비/실행 항목 조회
     */

    F103_ACTION_ITEM_404(
            HttpStatus.NOT_FOUND,
            "F103_ACTION_ITEM_404",
            "일정을 찾을 수 없습니다."
    ),

    F103_ACTION_ITEM_400(
            HttpStatus.BAD_REQUEST,
            "F103_ACTION_ITEM_400",
            "반복 일정 회차 날짜가 올바르지 않습니다."
    ),

    /**
     * F104: 캘린더 내 시간형 실행 항목 조회
     */
    F104_ACTION_ITEM_400(
            HttpStatus.BAD_REQUEST,
            "F104_ACTION_ITEM_400",
            "조회 날짜가 올바르지 않습니다."
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