package com.tryna.domain.reminder.dto;

public record AlarmStateResponse(
        boolean alarmState
) {

    public static AlarmStateResponse from(boolean alarmState) {
        return new AlarmStateResponse(alarmState);
    }
}
