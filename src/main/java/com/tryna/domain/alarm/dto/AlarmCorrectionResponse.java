package com.tryna.domain.alarm.dto;

import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.enums.TargetType;

import java.time.LocalDateTime;
import java.util.List;

public record AlarmCorrectionResponse(
        List<Item> alarms
) {

    public static AlarmCorrectionResponse from(List<Reminders> reminders) {
        return new AlarmCorrectionResponse(reminders.stream().map(Item::from).toList());
    }

    public record Item(
            Long reminderId,
            Long targetEventId,
            Long targetActionItemId,
            String alarmTitle,
            String alarmBody,
            LocalDateTime scheduledAt
    ) {

        public static Item from(Reminders reminder) {
            boolean isEvent = reminder.getTargetType() == TargetType.EVENT;
            return new Item(
                    reminder.getReminderId(),
                    isEvent ? reminder.getTargetEvent().getEventId() : null,
                    isEvent ? null : reminder.getTargetActionItem().getActionItemId(),
                    reminder.getAlarmTitle(),
                    reminder.getAlarmBody(),
                    reminder.getScheduledAt()
            );
        }
    }
}
