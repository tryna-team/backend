package com.tryna.domain.reminder.service;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.alarm.dto.ActionItemReminderResponse;
import com.tryna.domain.alarm.dto.AlarmCorrectionResponse;
import com.tryna.domain.alarm.dto.EventReminderResponse;
import com.tryna.domain.auth.repository.FcmTokenRedisRepository;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.reminder.entity.Reminders;
import com.tryna.domain.reminder.enums.ReminderStatus;
import com.tryna.domain.reminder.repository.AlarmDelayedQueueRepository;
import com.tryna.domain.reminder.repository.RemindersRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.AlarmErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * F100/F101/F102: 일정·준비/실행 항목 리마인드 알람의 생성, 수정(보정), 완료 취소를 담당한다.
 *
 * 실제 발송 스케줄링(Redis 지연 큐 적재)은 {@link AlarmDelayedQueueRepository}에,
 * 발송 자체는 {@link AlarmReminderDispatcher}에 위임한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmReminderScheduleService {

    private static final DateTimeFormatter EVENT_BODY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter EVENT_BODY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static final LocalTime ALL_DAY_ALARM_TIME = LocalTime.of(7, 0);
    static final int UNTIMED_PREP_LEAD_HOURS = 2;
    private static final int EVENT_REMINDER_LEAD_DAYS = 1;

    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final ActionItemsRepository actionItemsRepository;
    private final UserRepository userRepository;
    private final RemindersRepository remindersRepository;
    private final AlarmDelayedQueueRepository alarmDelayedQueueRepository;
    private final FcmTokenRedisRepository fcmTokenRedisRepository;

    @Transactional
    public EventReminderResponse createEventReminder(Long userId, Long eventId) {
        validateAlarmStateEnabled(userId, AlarmErrorCode.F101_EVENT_ALARM_403);

        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(AlarmErrorCode.F101_EVENT_ALARM_400_1));

        if (!userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)) {
            throw new BusinessException(AlarmErrorCode.F101_EVENT_ALARM_403);
        }

        validateHasPushToken(userId, AlarmErrorCode.F101_EVENT_ALARM_400_3);

        if (remindersRepository.existsByTargetEvent_EventIdAndReminderStatus(eventId, ReminderStatus.SCHEDULED)) {
            throw new BusinessException(AlarmErrorCode.F101_EVENT_ALARM_409);
        }

        LocalDateTime scheduledAt = computeEventReminderTime(event);
        validateFutureSchedule(scheduledAt, AlarmErrorCode.F101_EVENT_ALARM_400_2);

        Users user = findUser(userId);

        try {
            Reminders reminder = Reminders.createForEvent(
                    user,
                    event,
                    scheduledAt,
                    event.getTitle(),
                    buildEventAlarmBody(event)
            );

            Reminders saved = remindersRepository.save(reminder);
            alarmDelayedQueueRepository.schedule(saved.getReminderId(), scheduledAt);

            return EventReminderResponse.from(saved);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(AlarmErrorCode.F101_EVENT_ALARM_500);
        }
    }

    @Transactional
    public ActionItemReminderResponse createActionItemReminder(Long userId, Long actionItemId) {
        validateAlarmStateEnabled(userId, AlarmErrorCode.F102_ACTIONITEM_ALARM_403);

        ActionItems actionItem = actionItemsRepository.findByActionItemIdAndDeletedAtIsNull(actionItemId)
                .orElseThrow(() -> new BusinessException(AlarmErrorCode.F102_ACTIONITEM_ALARM_400_1));

        Long eventId = actionItem.getParentEvent().getEventId();
        if (!userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)) {
            throw new BusinessException(AlarmErrorCode.F102_ACTIONITEM_ALARM_403);
        }

        validateHasPushToken(userId, AlarmErrorCode.F102_ACTIONITEM_ALARM_400_3);

        if (remindersRepository.existsByTargetActionItem_ActionItemIdAndReminderStatus(actionItemId, ReminderStatus.SCHEDULED)) {
            throw new BusinessException(AlarmErrorCode.F102_ACTIONITEM_ALARM_409);
        }

        LocalDateTime scheduledAt = computeActionItemReminderTime(actionItem);
        validateFutureSchedule(scheduledAt, AlarmErrorCode.F102_ACTIONITEM_ALARM_400_2);

        Users user = findUser(userId);

        try {
            Reminders reminder = Reminders.createForActionItem(
                    user,
                    actionItem,
                    scheduledAt,
                    actionItem.getTitle(),
                    buildActionItemAlarmBody(actionItem)
            );

            Reminders saved = remindersRepository.save(reminder);
            alarmDelayedQueueRepository.schedule(saved.getReminderId(), scheduledAt);

            return ActionItemReminderResponse.from(saved);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(AlarmErrorCode.F102_ACTIONITEM_ALARM_500);
        }
    }

    /**
     * PATCH /api/v1/alarms/{eventId}
     *
     * C107 일정 수정 API가 이미 반영한 최신 일정/준비·실행 항목 정보를 기준으로,
     * 활성화(SCHEDULED)된 리마인더들의 발송 시각·제목·본문을 다시 계산하여 보정한다.
     */
    @Transactional
    public AlarmCorrectionResponse correctEventAlarms(Long userId, Long eventId) {
        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(AlarmErrorCode.F100_CORRECT_ALARM_400));

        if (!userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)) {
            throw new BusinessException(AlarmErrorCode.F100_CORRECT_ALARM_400);
        }

        try {
            List<Reminders> correctedReminders = new ArrayList<>();
            LocalDateTime eventScheduledAt = computeEventReminderTime(event);
            String eventTitle = event.getTitle();
            String eventAlarmBody = buildEventAlarmBody(event);

            remindersRepository.findAllByTargetEvent_EventIdAndReminderStatus(eventId, ReminderStatus.SCHEDULED)
                    .forEach(reminder -> {
                        if (isFuture(eventScheduledAt)) {
                            reminder.reschedule(eventScheduledAt, eventTitle, eventAlarmBody);
                            alarmDelayedQueueRepository.schedule(reminder.getReminderId(), eventScheduledAt);
                            correctedReminders.add(reminder);
                        } else {
                            cancelReminder(reminder);
                        }
                    });

            remindersRepository.findAllByTargetActionItem_ParentEvent_EventIdAndReminderStatus(eventId, ReminderStatus.SCHEDULED)
                    .forEach(reminder -> {
                        ActionItems actionItem = reminder.getTargetActionItem();
                        LocalDateTime scheduledAt = computeActionItemReminderTime(actionItem);
                        if (isFuture(scheduledAt)) {
                            reminder.reschedule(scheduledAt, actionItem.getTitle(), buildActionItemAlarmBody(actionItem));
                            alarmDelayedQueueRepository.schedule(reminder.getReminderId(), scheduledAt);
                            correctedReminders.add(reminder);
                        } else {
                            cancelReminder(reminder);
                        }
                    });

            return AlarmCorrectionResponse.from(correctedReminders);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(AlarmErrorCode.F100_CORRECT_ALARM_500);
        }
    }

    /**
     * 준비/실행 항목이 완료 처리되면(actionItems.completed_at 세팅), 발송 전 알람을 취소한다.
     */
    @Transactional
    public void cancelActionItemReminders(Long actionItemId) {
        remindersRepository
                .findAllByTargetActionItem_ActionItemIdAndReminderStatus(actionItemId, ReminderStatus.SCHEDULED)
                .forEach(reminder -> {
                    reminder.markCanceled();
                    alarmDelayedQueueRepository.cancel(reminder.getReminderId());
                });
    }

    private void validateAlarmStateEnabled(Long userId, AlarmErrorCode errorCode) {
        Users user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(errorCode));

        if (!user.isAlarmState()) {
            throw new BusinessException(errorCode);
        }
    }

    private void validateHasPushToken(Long userId, AlarmErrorCode errorCode) {
        if (fcmTokenRedisRepository.findAll(userId).isEmpty()) {
            throw new BusinessException(errorCode);
        }
    }

    private void validateFutureSchedule(LocalDateTime scheduledAt, AlarmErrorCode errorCode) {
        if (!isFuture(scheduledAt)) {
            throw new BusinessException(errorCode);
        }
    }

    private boolean isFuture(LocalDateTime scheduledAt) {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }

    private void cancelReminder(Reminders reminder) {
        reminder.markCanceled();
        alarmDelayedQueueRepository.cancel(reminder.getReminderId());
    }

    private Users findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_403));
    }

    // ---- 일정 리마인더 시각/문구 계산 ----

    public LocalDateTime computeEventReminderTime(Events event) {
        if (event.getStartDate() == null) {
            return null;
        }
        return computeEventReminderTimeForOccurrence(event, event.getStartDate());
    }

    /**
     * 특정 회차 날짜를 기준으로 알람 발송 시각을 계산한다. (반복 일정의 다음 주기 재스케줄링에도 사용)
     */
    public LocalDateTime computeEventReminderTimeForOccurrence(Events event, LocalDate occurrenceDate) {
        if (occurrenceDate == null) {
            return null;
        }

        if (Boolean.TRUE.equals(event.getIsAllDay()) || event.getStartDatetime() == null) {
            return LocalDateTime.of(occurrenceDate, ALL_DAY_ALARM_TIME);
        }

        LocalDateTime occurrenceStart = LocalDateTime.of(occurrenceDate, event.getStartDatetime().toLocalTime());
        return occurrenceStart.minusDays(EVENT_REMINDER_LEAD_DAYS);
    }

    /**
     * 발송된 리마인더의 scheduled_at으로부터 "방금 발송된 회차"의 날짜를 역산한다.
     */
    public LocalDate deriveEventOccurrenceDate(Reminders reminder, Events event) {
        LocalDate scheduledDate = reminder.getScheduledAt().toLocalDate();
        if (Boolean.TRUE.equals(event.getIsAllDay()) || event.getStartDatetime() == null) {
            return scheduledDate;
        }
        return scheduledDate.plusDays(EVENT_REMINDER_LEAD_DAYS);
    }

    public String buildEventAlarmBody(Events event) {
        String dateTimePart = formatEventStart(event);
        String description = event.getDescription() == null ? "" : event.getDescription().trim();
        return (dateTimePart + " " + description).trim();
    }

    private String formatEventStart(Events event) {
        if (event.getStartDate() == null) {
            return "";
        }
        if (!Boolean.TRUE.equals(event.getIsAllDay()) && event.getStartDatetime() != null) {
            return event.getStartDatetime().format(EVENT_BODY_DATE_TIME_FORMATTER);
        }
        return event.getStartDate().format(EVENT_BODY_DATE_FORMATTER);
    }

    // ---- 준비/실행 항목 리마인더 시각/문구 계산 ----

    public LocalDateTime computeActionItemReminderTime(ActionItems actionItem) {
        if (actionItem.getItemType() == ItemType.TIMED_ACTION) {
            if (actionItem.getDisplayDatetime() != null) {
                return actionItem.getDisplayDatetime();
            }
            if (actionItem.getDisplayDate() != null) {
                return LocalDateTime.of(actionItem.getDisplayDate(), ALL_DAY_ALARM_TIME);
            }
            return null;
        }

        LocalDateTime parentStart = resolveParentStart(actionItem.getParentEvent());
        return parentStart == null ? null : parentStart.minusHours(UNTIMED_PREP_LEAD_HOURS);
    }

    /**
     * 부모 일정의 다음 회차 날짜를 기준으로 준비/실행 항목 알람 시각을 다시 계산한다. (반복 알람 재스케줄링용)
     */
    public LocalDateTime computeActionItemReminderTimeForOccurrence(ActionItems actionItem, LocalDate occurrenceDate) {
        if (actionItem.getItemType() == ItemType.TIMED_ACTION) {
            LocalDate displayDate = actionItem.getOffsetDays() == null
                    ? occurrenceDate
                    : occurrenceDate.plusDays(actionItem.getOffsetDays());
            LocalTime timeOfDay = actionItem.getDisplayDatetime() != null
                    ? actionItem.getDisplayDatetime().toLocalTime()
                    : ALL_DAY_ALARM_TIME;
            return LocalDateTime.of(displayDate, timeOfDay);
        }

        LocalTime parentTimeOfDay = actionItem.getParentEvent().getStartDatetime() != null
                ? actionItem.getParentEvent().getStartDatetime().toLocalTime()
                : LocalTime.MIDNIGHT;
        return LocalDateTime.of(occurrenceDate, parentTimeOfDay).minusHours(UNTIMED_PREP_LEAD_HOURS);
    }

    /**
     * 발송된 리마인더의 scheduled_at으로부터 "방금 발송된 회차"의 부모 일정 회차 날짜를 역산한다.
     */
    public LocalDate deriveActionItemOccurrenceDate(Reminders reminder, ActionItems actionItem) {
        LocalDateTime scheduledAt = reminder.getScheduledAt();

        if (actionItem.getItemType() == ItemType.TIMED_ACTION) {
            LocalDate displayDate = scheduledAt.toLocalDate();
            if (actionItem.getOffsetDays() != null) {
                return displayDate.minusDays(actionItem.getOffsetDays());
            }

            Events parentEvent = actionItem.getParentEvent();
            if (parentEvent != null && Boolean.TRUE.equals(parentEvent.getIsRecurring())) {
                // offsetDays가 없는 반복 TIMED_ACTION은 displayDate가 곧 해당 회차 occurrence다.
                return displayDate;
            }

            return actionItem.getOccurrenceDate();
        }

        return scheduledAt.plusHours(UNTIMED_PREP_LEAD_HOURS).toLocalDate();
    }

    private LocalDateTime resolveParentStart(Events parentEvent) {
        if (parentEvent.getStartDatetime() != null) {
            return parentEvent.getStartDatetime();
        }
        if (parentEvent.getStartDate() != null) {
            return parentEvent.getStartDate().atStartOfDay();
        }
        return null;
    }

    public String buildActionItemAlarmBody(ActionItems actionItem) {
        return "✨ " + actionItem.getParentEvent().getTitle() + ". 준비해요!";
    }
}
