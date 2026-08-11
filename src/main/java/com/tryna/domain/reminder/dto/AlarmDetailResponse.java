package com.tryna.domain.reminder.dto;

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
        var targetEvent = reminder.getTargetEvent();
        var targetActionItem = reminder.getTargetActionItem();

        return new AlarmDetailResponse(
                reminder.getReminderId(),
                reminder.getTargetType(),
                reminder.getScheduledAt(),
                reminder.getAlarmTitle(),
                reminder.getAlarmBody(),
                targetEvent != null ? targetEvent.getEventId() : null,
                targetActionItem != null ? targetActionItem.getActionItemId() : null,
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
