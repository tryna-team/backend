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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmReminderDispatchExecutor {

    private final RemindersRepository remindersRepository;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;
    private final FcmPushService fcmPushService;
    private final ActionItemOccurrenceStatesRepository actionItemOccurrenceStatesRepository;
    private final AlarmReminderScheduleService alarmReminderScheduleService;
    private final AlarmDelayedQueueRepository alarmDelayedQueueRepository;

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

        PushDispatchResult dispatchResult = sendPush(reminder);
        switch (dispatchResult) {
            case SENT -> rescheduleIfRecurring(reminder);
            case RETRY -> log.warn("FCM 발송 재시도를 예약했습니다. reminderId={}", reminderId);
            case FAILED -> reminder.markFailed();
        }
    }

    private enum PushDispatchResult {
        SENT,
        RETRY,
        FAILED
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

    private PushDispatchResult sendPush(Reminders reminder) {
        Long userId = reminder.getUser().getUserId();
        Set<String> tokens = fcmTokenRedisRepository.findAll(userId);

        if (tokens.isEmpty()) {
            log.warn("등록된 FCM 토큰이 없어 리마인더를 발송하지 못했습니다. reminderId={}, userId={}",
                    reminder.getReminderId(), userId);
            return PushDispatchResult.FAILED;
        }

        Map<String, String> data = buildPushData(reminder);
        boolean anySuccess = false;
        boolean shouldRetry = false;

        for (FcmPushService.TokenDeliveryOutcome outcome : fcmPushService.sendToTokens(
                tokens,
                reminder.getAlarmTitle(),
                reminder.getAlarmBody(),
                data
        )) {
            try {
                switch (outcome.result()) {
                    case SUCCESS -> anySuccess = true;
                    case UNREGISTERED -> fcmTokenRedisRepository.remove(userId, outcome.token());
                    case TRANSIENT_FAILURE, UNAVAILABLE -> shouldRetry = true;
                }
            } catch (Exception e) {
                log.error("FCM 토큰별 발송 결과 처리 중 오류가 발생했습니다. reminderId={}, token={}",
                        reminder.getReminderId(), outcome.token(), e);
                shouldRetry = true;
            }
        }

        if (anySuccess) {
            return PushDispatchResult.SENT;
        }
        if (shouldRetry) {
            alarmDelayedQueueRepository.reEnqueue(reminder.getReminderId());
            return PushDispatchResult.RETRY;
        }
        return PushDispatchResult.FAILED;
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
