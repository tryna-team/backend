package com.tryna.domain.event.service;

import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.dto.EventDeleteRequest;
import com.tryna.domain.event.dto.EventDeleteResponse;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.DeleteScope;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.RecurringEventExceptionType;
import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.RecurringEventExceptionsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.domain.reminder.service.ReminderLifecycleService;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import com.tryna.global.exception.EventErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventDeletionService {

    private static final EnumSet<EventStatus> DELETABLE_EVENT_STATUSES = EnumSet.of(
            EventStatus.CONFIRMED,
            EventStatus.NEEDS_CONFIRMATION
    );

    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final ActionItemsRepository actionItemsRepository;
    private final RecurringEventExceptionsRepository recurringEventExceptionsRepository;
    private final ReminderLifecycleService reminderLifecycleService;

    @Transactional
    public boolean softDeleteEvent(Long eventId) {
        int updated = eventsRepository.softDeleteById(eventId, LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }

        reminderLifecycleService.cancelScheduledForSoftDeletedEvent(eventId);
        return true;
    }

    @Transactional
    public EventDeleteResponse deleteEvent(Long userId, Long eventId, EventDeleteRequest request) {
        validateDeleteRequest(request);

        if (!eventsRepository.existsVisibleByEventIdAndEventStatusIn(eventId, DELETABLE_EVENT_STATUSES)) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_404);
        }

        Events event = eventsRepository.findVisibleEventAccessibleToUser(
                        userId,
                        eventId,
                        DELETABLE_EVENT_STATUSES,
                        ConnectionStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_403));

        validateDeletableEvent(userId, event, request);

        if (Boolean.TRUE.equals(event.getIsRecurring())) {
            return deleteRecurringEvent(event, request);
        }

        if (request.deleteScope() != DeleteScope.SINGLE) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_400);
        }

        return deleteWholeEvent(eventId, request.deleteScope());
    }

    private EventDeleteResponse deleteWholeEvent(Long eventId, DeleteScope deleteScope) {
        LocalDateTime deletedAt = LocalDateTime.now();
        reminderLifecycleService.cancelScheduledForSoftDeletedActionItemsByParentEvent(eventId);
        int affectedActionItemCount = actionItemsRepository.softDeleteByParentEventId(eventId, deletedAt);
        int affectedEventCount = eventsRepository.softDeleteById(eventId, deletedAt);

        if (affectedEventCount <= 0) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_404);
        }

        reminderLifecycleService.cancelScheduledForSoftDeletedEvent(eventId);

        return new EventDeleteResponse(
                eventId,
                deleteScope,
                EventStatus.DELETED,
                affectedEventCount,
                affectedActionItemCount
        );
    }

    private EventDeleteResponse deleteRecurringEvent(Events event, EventDeleteRequest request) {
        LocalDate occurrenceDate = request.occurrenceDate();
        if (occurrenceDate == null || !isRecurringOccurrenceOn(event, occurrenceDate)) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_400);
        }

        if (request.deleteScope() == DeleteScope.THIS_AND_FUTURE) {
            return deleteRecurringThisAndFuture(event, occurrenceDate);
        }

        return deleteRecurringSingleOccurrence(event, occurrenceDate);
    }

    private EventDeleteResponse deleteRecurringSingleOccurrence(Events event, LocalDate occurrenceDate) {
        recurringEventExceptionsRepository.insertDeletedOccurrenceIfAbsent(
                event.getEventId(),
                occurrenceDate,
                RecurringEventExceptionType.DELETED.name()
        );

        LocalDateTime deletedAt = LocalDateTime.now();
        reminderLifecycleService.cancelScheduledForSoftDeletedActionItemsByParentEventAndDisplayDate(
                event.getEventId(),
                occurrenceDate
        );
        int affectedActionItemCount = actionItemsRepository.softDeleteByParentEventIdAndDisplayDate(
                event.getEventId(),
                occurrenceDate,
                deletedAt
        );

        return new EventDeleteResponse(
                event.getEventId(),
                DeleteScope.SINGLE,
                EventStatus.DELETED,
                1,
                affectedActionItemCount
        );
    }

    private EventDeleteResponse deleteRecurringThisAndFuture(Events event, LocalDate occurrenceDate) {
        if (occurrenceDate.equals(event.getStartDate())) {
            return deleteWholeEvent(event.getEventId(), DeleteScope.THIS_AND_FUTURE);
        }

        LocalDateTime deletedAt = LocalDateTime.now();
        LocalDateTime recurrenceEndDate = occurrenceDate.minusDays(1).atTime(LocalTime.MAX);
        event.truncateRecurrenceEndDate(recurrenceEndDate);

        reminderLifecycleService.cancelScheduledForSoftDeletedActionItemsByParentEventFromDisplayDate(
                event.getEventId(),
                occurrenceDate
        );
        int affectedActionItemCount = actionItemsRepository.softDeleteByParentEventIdFromDisplayDate(
                event.getEventId(),
                occurrenceDate,
                deletedAt
        );

        return new EventDeleteResponse(
                event.getEventId(),
                DeleteScope.THIS_AND_FUTURE,
                EventStatus.DELETED,
                1,
                affectedActionItemCount
        );
    }

    private void validateDeleteRequest(EventDeleteRequest request) {
        if (request == null
                || request.deleteScope() == null
                || !Boolean.TRUE.equals(request.cascade())) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_400);
        }
    }

    private void validateDeletableEvent(Long userId, Events event, EventDeleteRequest request) {
        if (event.getSourceType() == SourceType.EXTERNAL_CALENDAR) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_400);
        }

        if (!userEventsRepository.existsOwnerByUserIdAndEventId(userId, event.getEventId())) {
            throw new BusinessException(CommonErrorCode.COMMON_403);
        }

    }

    private boolean isRecurringOccurrenceOn(Events event, LocalDate date) {
        if (!Boolean.TRUE.equals(event.getIsRecurring())
                || event.getStartDate() == null
                || date.isBefore(event.getStartDate())
                || event.getRecurrenceType() == null
                || event.getRecurrenceType() == RecurrenceType.NONE
                || event.getRecurrenceType() == RecurrenceType.CUSTOM) {
            return false;
        }

        if (event.getRecurrenceEndDate() != null
                && date.isAfter(event.getRecurrenceEndDate().toLocalDate())) {
            return false;
        }

        int interval = event.getRecurrenceInterval() == null ? 1 : event.getRecurrenceInterval();
        if (interval < 1) {
            return false;
        }

        return switch (event.getRecurrenceType()) {
            case DAILY -> ChronoUnit.DAYS.between(event.getStartDate(), date) % interval == 0;
            case WEEKLY -> isWeeklyOccurrence(event, date, interval);
            case MONTHLY -> isMonthlyOccurrence(event, date, interval);
            case YEARLY -> isYearlyOccurrence(event, date, interval);
            case NONE, CUSTOM -> false;
        };
    }

    private boolean isWeeklyOccurrence(Events event, LocalDate date, int interval) {
        RecurrenceDayOfWeek expectedDayOfWeek = event.getRecurrenceDayOfWeek();
        if (expectedDayOfWeek == null || expectedDayOfWeek == RecurrenceDayOfWeek.NONE) {
            expectedDayOfWeek = toRecurrenceDayOfWeek(event.getStartDate());
        }

        return expectedDayOfWeek == toRecurrenceDayOfWeek(date)
                && ChronoUnit.WEEKS.between(event.getStartDate(), date) % interval == 0;
    }

    private boolean isMonthlyOccurrence(Events event, LocalDate date, int interval) {
        Integer expectedDayOfMonth = event.getRecurrenceDayOfMonth();
        if (expectedDayOfMonth == null || date.getDayOfMonth() != expectedDayOfMonth) {
            return false;
        }

        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(event.getStartDate()),
                YearMonth.from(date)
        );
        return months % interval == 0;
    }

    private boolean isYearlyOccurrence(Events event, LocalDate date, int interval) {
        Integer expectedDayOfMonth = event.getRecurrenceDayOfMonth();
        if (expectedDayOfMonth == null
                || date.getMonthValue() != event.getStartDate().getMonthValue()
                || date.getDayOfMonth() != expectedDayOfMonth) {
            return false;
        }

        long years = ChronoUnit.YEARS.between(event.getStartDate(), date);
        return years % interval == 0;
    }

    private RecurrenceDayOfWeek toRecurrenceDayOfWeek(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> RecurrenceDayOfWeek.MON;
            case TUESDAY -> RecurrenceDayOfWeek.TUE;
            case WEDNESDAY -> RecurrenceDayOfWeek.WED;
            case THURSDAY -> RecurrenceDayOfWeek.THU;
            case FRIDAY -> RecurrenceDayOfWeek.FRI;
            case SATURDAY -> RecurrenceDayOfWeek.SAT;
            case SUNDAY -> RecurrenceDayOfWeek.SUN;
        };
    }
}
