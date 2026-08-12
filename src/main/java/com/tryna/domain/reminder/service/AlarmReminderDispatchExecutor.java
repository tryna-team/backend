package com.tryna.domain.reminder.service;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.repository.ActionItemOccurrenceStatesRepository;
import com.tryna.domain.reminder.service.FcmPushService;
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

    private static final int MAX_OCCURRENCE_ADVANCES = 366;

    private final RemindersRepository remindersRepository;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;
    private final FcmPushService fcmPushService;
    private final ActionItemOccurrenceStatesRepository actionItemOccurrenceStatesRepository;
    private final AlarmReminderScheduleService alarmReminderScheduleService;
    private final AlarmDelayedQueueRepository alarmDelayedQueueRepository;
    private final AlarmReminderQueueRelayService alarmReminderQueueRelayService;

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
                    // payload 문제(PERMANENT_FAILURE)는 토큰 자체는 유효할 수 있으므로 토큰을 유지하고 재시도하지 않는다.
                    case PERMANENT_FAILURE -> log.warn(
                            "FCM 발송 payload 오류로 토큰을 유지합니다. reminderId={}",
                            reminder.getReminderId());
                }
            } catch (Exception e) {
                log.error("FCM 토큰별 발송 결과 처리 중 오류가 발생했습니다. reminderId={}",
                        reminder.getReminderId(), e);
                shouldRetry = true;
            }
        }

        if (anySuccess) {
            alarmDelayedQueueRepository.clearRetryCount(reminder.getReminderId());
            return PushDispatchResult.SENT;
        }
        if (shouldRetry) {
            boolean reEnqueued = alarmDelayedQueueRepository.reEnqueue(reminder.getReminderId());
            if (reEnqueued) {
                return PushDispatchResult.RETRY;
            }
            log.warn("최대 재시도 횟수를 초과하여 리마인더 발송을 중단합니다. reminderId={}", reminder.getReminderId());
        }
        return PushDispatchResult.FAILED;
    }


    @Transactional
    public void handleDispatchFailure(Long reminderId) {
        boolean reEnqueued = alarmDelayedQueueRepository.reEnqueue(reminderId);
        if (!reEnqueued) {
            remindersRepository.findById(reminderId).ifPresent(Reminders::markFailed);
        }
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
        LocalDateTime nextScheduledAt = findNextFutureEventReminderTime(event, currentOccurrenceDate);

        if (nextScheduledAt == null) {
            reminder.markSent();
            return;
        }

        reminder.reschedule(nextScheduledAt, event.getTitle(), alarmReminderScheduleService.buildEventAlarmBody(event));
        alarmReminderQueueRelayService.enqueueSchedule(reminder.getReminderId(), nextScheduledAt);
    }

    private void rescheduleActionItemReminder(Reminders reminder) {
        ActionItems actionItem = reminder.getTargetActionItem();
        Events parentEvent = actionItem.getParentEvent();

        if (!Boolean.TRUE.equals(parentEvent.getIsRecurring())) {
            reminder.markSent();
            return;
        }

        LocalDate currentOccurrenceDate = alarmReminderScheduleService.deriveActionItemOccurrenceDate(reminder, actionItem);
        LocalDateTime nextScheduledAt = findNextFutureActionItemReminderTime(actionItem, currentOccurrenceDate);

        if (nextScheduledAt == null) {
            reminder.markSent();
            return;
        }

        reminder.reschedule(nextScheduledAt, actionItem.getTitle(),
                alarmReminderScheduleService.buildActionItemAlarmBody(actionItem));
        alarmReminderQueueRelayService.enqueueSchedule(reminder.getReminderId(), nextScheduledAt);
    }

    private LocalDateTime findNextFutureEventReminderTime(Events event, LocalDate currentOccurrenceDate) {
        LocalDate occurrenceDate = currentOccurrenceDate;
        LocalDateTime now = LocalDateTime.now();

        for (int attempt = 0; attempt < MAX_OCCURRENCE_ADVANCES; attempt++) {
            LocalDate nextOccurrenceDate = EventRecurrenceCalculator.nextOccurrenceDateAfter(event, occurrenceDate);
            if (nextOccurrenceDate == null) {
                return null;
            }

            LocalDateTime scheduledAt =
                    alarmReminderScheduleService.computeEventReminderTimeForOccurrence(event, nextOccurrenceDate);
            if (scheduledAt != null && scheduledAt.isAfter(now)) {
                return scheduledAt;
            }

            occurrenceDate = nextOccurrenceDate;
        }

        return null;
    }

    private LocalDateTime findNextFutureActionItemReminderTime(ActionItems actionItem, LocalDate currentOccurrenceDate) {
        Events parentEvent = actionItem.getParentEvent();
        LocalDate occurrenceDate = currentOccurrenceDate;
        LocalDateTime now = LocalDateTime.now();

        for (int attempt = 0; attempt < MAX_OCCURRENCE_ADVANCES; attempt++) {
            LocalDate nextOccurrenceDate = EventRecurrenceCalculator.nextOccurrenceDateAfter(parentEvent, occurrenceDate);
            if (nextOccurrenceDate == null) {
                return null;
            }

            LocalDateTime scheduledAt =
                    alarmReminderScheduleService.computeActionItemReminderTimeForOccurrence(actionItem, nextOccurrenceDate);
            if (scheduledAt != null && scheduledAt.isAfter(now)) {
                return scheduledAt;
            }

            occurrenceDate = nextOccurrenceDate;
        }

        return null;
    }
}
