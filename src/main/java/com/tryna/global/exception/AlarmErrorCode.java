package com.tryna.global.exception;

import org.springframework.http.HttpStatus;

public enum AlarmErrorCode implements ErrorCode {

    F100_ALARM_TERM_400(
            HttpStatus.BAD_REQUEST,
            "F100_ALARM_TERM_400",
            "알람 약관 동의를 거절했습니다."
    ),

    F100_ALARM_TOOGLE_400(
            HttpStatus.BAD_REQUEST,
            "F100_ALARM_TOOGLE_400",
            "알람 약관에 미동의 되어 있습니다."
    ),

    F100_ALARM_TOOGLE_MVP_400(
            HttpStatus.BAD_REQUEST,
            "F100_ALARM_TOOGLE_MVP_400",
            "알람 상태 전환을 할 수 없습니다."
    ),

    F100_ALARM_STATUS_400(
            HttpStatus.BAD_REQUEST,
            "F100_ALARM_STATUS_400",
            "알람 상태 조회가 불가능 합니다."
    ),

    F100_ALARM_404(
            HttpStatus.NOT_FOUND,
            "F100_ALARM_404",
            "알람을 찾을 수 없습니다."
    ),

    F100_ALARMLIST_400_1(
            HttpStatus.BAD_REQUEST,
            "F100_ALARMLIST_400_1",
            "요청한 size 값이 유효한 범위(1~100)를 벗어났습니다."
    ),

    F100_ALARMLIST_400_2(
            HttpStatus.BAD_REQUEST,
            "F100_ALARMLIST_400_2",
            "잘못된 형식의 cursor 값입니다. 올바른 커서 문자열을 전달해주세요."
    ),

    F100_ALARMLIST_400_3(
            HttpStatus.BAD_REQUEST,
            "F100_ALARMLIST_400_3",
            "요청 파라미터의 타입이 올바르지 않습니다."
    ),

    F100_ALARMLIST_409(
            HttpStatus.CONFLICT,
            "F100_ALARMLIST_409",
            "현재 알람 목록을 조회할 수 없는 사용자 상태(예: 탈퇴 처리 중)입니다."
    ),

    F100_ALARMLIST_500(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "F100_ALARMLIST_500",
            "알람 목록 조회 중 서버 내부 에러가 발생하였습니다."
    ),

    F100_PUSH_TOKEN_400(
            HttpStatus.BAD_REQUEST,
            "F100_PUSH_TOKEN_400",
            "유효하지 않은 푸시 토큰입니다."
    ),

    F100_PUSH_TOKEN_409(
            HttpStatus.CONFLICT,
            "F100_PUSH_TOKEN_409",
            "이미 푸시 토큰이 있는 사용자 입니다."
    ),

    F100_PUSH_TOKEN_500(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "F100_PUSH_TOKEN_500",
            "푸시 토큰 발급 중 서버 에러가 발생하였습니다."
    ),

    F100_CORRECT_ALARM_400(
            HttpStatus.BAD_REQUEST,
            "F100_CORRECT_ALARM_400",
            "존재하지 않는 Path 파라미터 입니다."
    ),

    F100_CORRECT_ALARM_500(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "F100_CORRECT_ALARM_500",
            "알람 수정 중 서버 에러가 발생하였습니다."
    ),

    F101_EVENT_ALARM_400_1(
            HttpStatus.BAD_REQUEST,
            "F101_EVEMT_ALARM_400_1",
            "존재하지 않는 Path 파라미터 입니다."
    ),

    F101_EVENT_ALARM_400_2(
            HttpStatus.BAD_REQUEST,
            "F101_EVEMT_ALARM_400_2",
            "알람 일정의 시간이 과거입니다."
    ),

    F101_EVENT_ALARM_400_3(
            HttpStatus.BAD_REQUEST,
            "F101_EVEMT_ALARM_400_3",
            "유효하지 않은 푸시 토큰입니다."
    ),

    F101_EVENT_ALARM_403(
            HttpStatus.FORBIDDEN,
            "F101_EVENT_ALARM_403",
            "해당 일정에 알람을 설정할 권한이 없습니다."
    ),

    F101_EVENT_ALARM_409(
            HttpStatus.CONFLICT,
            "F101_EVENT_ALARM_409",
            "해당 일정에 이미 활성화된 동일한 알람이 존재합니다."
    ),

    F101_EVENT_ALARM_500(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "F101_EVENT_ALARM_500",
            "일정 리마인드 푸시 알람 전송 중 서버 에러가 발생하였습니다."
    ),

    F102_ACTIONITEM_ALARM_400_1(
            HttpStatus.BAD_REQUEST,
            "F102_ACTIONITEM_ALARM_400_1",
            "존재하지 않는 Path 파라미터 입니다."
    ),

    F102_ACTIONITEM_ALARM_400_2(
            HttpStatus.BAD_REQUEST,
            "F102_ACTIONITEM_ALARM_400_2",
            "알람 일정의 시간이 과거입니다."
    ),

    F102_ACTIONITEM_ALARM_400_3(
            HttpStatus.BAD_REQUEST,
            "F102_ACTIONITEM_ALARM_400_3",
            "유효하지 않은 푸시 토큰입니다."
    ),

    F102_ACTIONITEM_ALARM_403(
            HttpStatus.FORBIDDEN,
            "F102_ACTIONITEM_ALARM_403",
            "해당 일정에 알람을 설정할 권한이 없습니다."
    ),

    F102_ACTIONITEM_ALARM_409(
            HttpStatus.CONFLICT,
            "F102_ACTIONITEM_ALARM_409",
            "해당 일정에 이미 활성화된 동일한 알람이 존재합니다."
    ),

    F102_ACTIONITEM_ALARM_500(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "F102_ACTIONITEM_ALARM_500",
            "일정 리마인드 푸시 알람 전송 중 서버 에러가 발생하였습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AlarmErrorCode(HttpStatus httpStatus, String code, String message) {
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
