package com.tryna.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.dto.EventDeleteRequest;
import com.tryna.domain.event.dto.EventDeleteResponse;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.DeleteScope;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.RecurringEventExceptionsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.domain.reminder.service.ReminderLifecycleService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventDeletionServiceTest {

    private EventsRepository eventsRepository;
    private UserEventsRepository userEventsRepository;
    private ActionItemsRepository actionItemsRepository;
    private RecurringEventExceptionsRepository recurringEventExceptionsRepository;
    private ReminderLifecycleService reminderLifecycleService;
    private EventDeletionService eventDeletionService;

    @BeforeEach
    void setUp() {
        eventsRepository = mock(EventsRepository.class);
        userEventsRepository = mock(UserEventsRepository.class);
        actionItemsRepository = mock(ActionItemsRepository.class);
        recurringEventExceptionsRepository = mock(RecurringEventExceptionsRepository.class);
        reminderLifecycleService = mock(ReminderLifecycleService.class);
        eventDeletionService = new EventDeletionService(
                eventsRepository,
                userEventsRepository,
                actionItemsRepository,
                recurringEventExceptionsRepository,
                reminderLifecycleService
        );
    }

    @Test
    void singleRecurringOccurrenceDeletesOnlyOccurrenceSpecificActionItems() {
        Long userId = 1L;
        Long eventId = 10L;
        LocalDate occurrenceDate = LocalDate.of(2026, 8, 10);
        Events event = mockWeeklyRecurringEvent(eventId, occurrenceDate);
        EventDeleteRequest request = new EventDeleteRequest(DeleteScope.SINGLE, true, occurrenceDate);

        when(eventsRepository.existsVisibleByEventIdAndEventStatusIn(eq(eventId), anySet()))
                .thenReturn(true);
        when(eventsRepository.findVisibleEventAccessibleToUser(
                eq(userId),
                eq(eventId),
                anySet(),
                eq(ConnectionStatus.ACTIVE)
        )).thenReturn(Optional.of(event));
        when(userEventsRepository.existsOwnerByUserIdAndEventId(userId, eventId)).thenReturn(true);
        when(actionItemsRepository.softDeleteOccurrenceSpecificItems(
                eq(eventId),
                eq(occurrenceDate),
                any(LocalDateTime.class)
        )).thenReturn(2);

        EventDeleteResponse response = eventDeletionService.deleteEvent(userId, eventId, request);

        assertThat(response.affectedActionItemCount()).isEqualTo(2);
        verify(actionItemsRepository).softDeleteOccurrenceSpecificItems(
                eq(eventId),
                eq(occurrenceDate),
                any(LocalDateTime.class)
        );
        verify(actionItemsRepository, never()).softDeleteByParentEventIdAndOccurrenceDate(
                eq(eventId),
                eq(occurrenceDate),
                any(LocalDateTime.class)
        );
        verify(reminderLifecycleService)
                .cancelScheduledForSoftDeletedActionItemsByParentEventAndOccurrenceDate(
                        eventId,
                        occurrenceDate
                );
    }

    private Events mockWeeklyRecurringEvent(Long eventId, LocalDate startDate) {
        Events event = mock(Events.class);
        when(event.getEventId()).thenReturn(eventId);
        when(event.getIsRecurring()).thenReturn(true);
        when(event.getStartDate()).thenReturn(startDate);
        when(event.getRecurrenceType()).thenReturn(RecurrenceType.WEEKLY);
        when(event.getRecurrenceInterval()).thenReturn(1);
        when(event.getRecurrenceDayOfWeek()).thenReturn(RecurrenceDayOfWeek.MON);
        when(event.getRecurrenceEndDate()).thenReturn(startDate.plusYears(1).atStartOfDay());
        return event;
    }
}
