package com.tryna.domain.reminder.service;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.repository.ActionItemOccurrenceStatesRepository;
import com.tryna.domain.alarm.service.FcmPushService;
import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.util.EventRecurrenceCalculator;
import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.enums.TargetType;
import com.tryna.domain.reminder.repository.AlarmDelayedQueueRepository;
import com.tryna.domain.reminder.repository.RemindersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 지연 큐(ZSET)를 주기적으로 폴링하여 발송 시각이 도래한 리마인더를 FCM으로 발송하는 컨슈머.
 *
 * - 발송 대상이 이미 삭제되었거나(soft delete), 준비/실행 항목이 완료 처리된 경우에는 발송하지 않는다. (Soft Skip)
 * - 반복 일정/반복 일정에 연결된 준비·실행 항목의 알람은 발송 직후 다음 회차로 재계산하여 다시 큐에 적재한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmReminderDispatcher {

    private static final int BATCH_SIZE = 50;

    private final AlarmDelayedQueueRepository alarmDelayedQueueRepository;
    private final RemindersRepository remindersRepository;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;
    private final FcmPushService fcmPushService;
    private final ActionItemOccurrenceStatesRepository actionItemOccurrenceStatesRepository;
    private final AlarmReminderScheduleService alarmReminderScheduleService;

    @Scheduled(fixedDelay = 15000)
    public void dispatchDueReminders() {
        List<Long> dueReminderIds = alarmDelayedQueueRepository.popDue(BATCH_SIZE);

        for (Long reminderId : dueReminderIds) {
            try {
                dispatchOne(reminderId);
            } catch (Exception e) {
                log.error("리마인더 발송 처리 중 오류가 발생했습니다. reminderId={}", reminderId, e);
            }
        }
    }

    @Transactional
    public void dispatchOne(Long reminderId) {
        Reminders reminder = remindersRepository.findById(reminderId).orElse(null);
        if (reminder == null || !reminder.isScheduled()) {
            return;
        }

        if (isSuppressed(reminder)) {
            reminder.markSkipped();
            return;
        }

        boolean sent = sendPush(reminder);
        if (!sent) {
            reminder.markFailed();
            return;
        }

        rescheduleIfRecurring(reminder);
    }

    private boolean isSuppressed(Reminders reminder) {
        if (reminder.getTargetType() == TargetType.EVENT) {
            Events event = reminder.getTargetEvent();
            return event == null || event.getDeletedAt() != null;
        }

        ActionItems actionItem = reminder.getTargetActionItem();
        if (actionItem == null || actionItem.getDeletedAt() != null
                || actionItem.getParentEvent() == null || actionItem.getParentEvent().getDeletedAt() != null) {
            return true;
        }

        // 요구사항: action_items.completed_at 이 채워진(완료 처리된) 항목은 발송하지 않는다.
        if (actionItem.getCompletedAt() != null) {
            return true;
        }

        if (Boolean.TRUE.equals(actionItem.getParentEvent().getIsRecurring())) {
            LocalDate occurrenceDate = alarmReminderScheduleService.deriveActionItemOccurrenceDate(reminder, actionItem);
            if (occurrenceDate != null) {
                return actionItemOccurrenceStatesRepository
                        .findByActionItem_ActionItemIdAndOccurrenceDate(actionItem.getActionItemId(), occurrenceDate)
                        .map(state -> state.getCompletedAt() != null)
                        .orElse(false);
            }
        }

        return false;
    }

    private boolean sendPush(Reminders reminder) {
        Long userId = reminder.getUser().getUserId();
        Set<String> tokens = fcmTokenRedisRepository.findAll(userId);

        if (tokens.isEmpty()) {
            log.warn("등록된 FCM 토큰이 없어 리마인더를 발송하지 못했습니다. reminderId={}, userId={}",
                    reminder.getReminderId(), userId);
            return false;
        }

        Map<String, String> data = buildPushData(reminder);
        tokens.forEach(token -> fcmPushService.send(token, reminder.getAlarmTitle(), reminder.getAlarmBody(), data));
        return true;
    }

    private Map<String, String> buildPushData(Reminders reminder) {
        Map<String, String> data = new HashMap<>();
        data.put("reminderId", String.valueOf(reminder.getReminderId()));

        if (reminder.getTargetType() == TargetType.EVENT) {
            data.put("targetType", "EVENT");
            data.put("eventId", String.valueOf(reminder.getTargetEvent().getEventId()));
        } else {
            data.put("targetType", "TIMED_ACTION");
            data.put("actionItemId", String.valueOf(reminder.getTargetActionItem().getActionItemId()));
        }

        return data;
    }

    private void rescheduleIfRecurring(Reminders reminder) {
        if (reminder.getTargetType() == TargetType.EVENT) {
            rescheduleEventReminder(reminder);
        } else {
            rescheduleActionItemReminder(reminder);
        }
    }

    private void rescheduleEventReminder(Reminders reminder) {
        Events event = reminder.getTargetEvent();

        if (!Boolean.TRUE.equals(event.getIsRecurring())) {
            reminder.markSent();
            return;
        }

        LocalDate currentOccurrenceDate = alarmReminderScheduleService.deriveEventOccurrenceDate(reminder, event);
        LocalDate nextOccurrenceDate = EventRecurrenceCalculator.nextOccurrenceDateAfter(event, currentOccurrenceDate);

        if (nextOccurrenceDate == null) {
            reminder.markSent();
            return;
        }

        LocalDateTime nextScheduledAt =
                alarmReminderScheduleService.computeEventReminderTimeForOccurrence(event, nextOccurrenceDate);

        reminder.reschedule(nextScheduledAt, event.getTitle(), reminder.getAlarmBody());
        alarmDelayedQueueRepository.schedule(reminder.getReminderId(), nextScheduledAt);
    }

    private void rescheduleActionItemReminder(Reminders reminder) {
        ActionItems actionItem = reminder.getTargetActionItem();
        Events parentEvent = actionItem.getParentEvent();

        if (!Boolean.TRUE.equals(parentEvent.getIsRecurring())) {
            reminder.markSent();
            return;
        }

        LocalDate currentOccurrenceDate = alarmReminderScheduleService.deriveActionItemOccurrenceDate(reminder, actionItem);
        LocalDate nextOccurrenceDate = EventRecurrenceCalculator.nextOccurrenceDateAfter(parentEvent, currentOccurrenceDate);

        if (nextOccurrenceDate == null) {
            reminder.markSent();
            return;
        }

        LocalDateTime nextScheduledAt =
                alarmReminderScheduleService.computeActionItemReminderTimeForOccurrence(actionItem, nextOccurrenceDate);

        reminder.reschedule(nextScheduledAt, actionItem.getTitle(), reminder.getAlarmBody());
        alarmDelayedQueueRepository.schedule(reminder.getReminderId(), nextScheduledAt);
    }
}
