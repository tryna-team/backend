package com.tryna.domain.alarm.dto;

public record AlarmStateResponse(
        boolean alarmState
) {

    public static AlarmStateResponse from(boolean alarmState) {
        return new AlarmStateResponse(alarmState);
    }
}
