package com.tryna.domain.alarm.dto;

import com.tryna.domain.reminder.entity.Reminders;

import java.time.LocalDateTime;

public record EventReminderResponse(
        Long reminderId,
        String alarmTitle,
        String alarmBody,
        LocalDateTime scheduledAt
) {

    public static EventReminderResponse from(Reminders reminder) {
        return new EventReminderResponse(
                reminder.getReminderId(),
                reminder.getAlarmTitle(),
                reminder.getAlarmBody(),
                reminder.getScheduledAt()
        );
    }
}
