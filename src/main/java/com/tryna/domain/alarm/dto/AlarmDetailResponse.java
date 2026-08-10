package com.tryna.domain.alarm.dto;

import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.enums.TargetType;

import java.time.LocalDateTime;

public record AlarmDetailResponse(
        Long reminderId,
        TargetType targetType,
        LocalDateTime scheduledAt,
        String alarmTitle,
        String alarmBody,
        Long eventId,
        Long actionItemId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AlarmDetailResponse from(Reminders reminder) {
        boolean isEvent = reminder.getTargetType() == TargetType.EVENT;
        return new AlarmDetailResponse(
                reminder.getReminderId(),
                reminder.getTargetType(),
                reminder.getScheduledAt(),
                reminder.getAlarmTitle(),
                reminder.getAlarmBody(),
                isEvent ? reminder.getTargetEvent().getEventId() : null,
                isEvent ? null : reminder.getTargetActionItem().getActionItemId(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
