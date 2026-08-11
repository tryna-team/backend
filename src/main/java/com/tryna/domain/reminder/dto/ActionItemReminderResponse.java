package com.tryna.domain.reminder.dto;

import com.tryna.domain.reminder.entity.Reminders;

import java.time.LocalDateTime;

public record ActionItemReminderResponse(
        Long reminderId,
        String alarmTitle,
        String alarmBody,
        LocalDateTime scheduledAt
) {

    public static ActionItemReminderResponse from(Reminders reminder) {
        return new ActionItemReminderResponse(
                reminder.getReminderId(),
                reminder.getAlarmTitle(),
                reminder.getAlarmBody(),
                reminder.getScheduledAt()
        );
    }
}
