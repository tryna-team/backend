package com.tryna.domain.action.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tryna.domain.action.dto.EventActionItemResponse;
import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.repository.ActionItemOccurrenceStatesRepository;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.recommendation.repository.RecommendationFeedbacksRepository;
import com.tryna.domain.reminder.service.AlarmReminderScheduleService;
import com.tryna.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionItemServiceTest {

    private ActionItemsRepository actionItemsRepository;
    private EventsRepository eventsRepository;
    private UserEventsRepository userEventsRepository;
    private ActionItemService actionItemService;

    @BeforeEach
    void setUp() {
        actionItemsRepository = mock(ActionItemsRepository.class);
        eventsRepository = mock(EventsRepository.class);
        userEventsRepository = mock(UserEventsRepository.class);
        actionItemService = new ActionItemService(
                actionItemsRepository,
                mock(ActionItemOccurrenceStatesRepository.class),
                mock(RecommendationFeedbacksRepository.class),
                eventsRepository,
                userEventsRepository,
                mock(UserRepository.class),
                mock(AlarmReminderScheduleService.class)
        );
    }

    @Test
    void nonRecurringMultiDayEventReturnsSameItemsWithoutOccurrenceDateFiltering() {
        Long userId = 1L;
        Long eventId = 10L;
        Events event = mock(Events.class);
        ActionItems actionItem = mock(ActionItems.class);

        when(event.getIsRecurring()).thenReturn(false);
        when(eventsRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)).thenReturn(true);
        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(eventId))
                .thenReturn(List.of(actionItem));

        EventActionItemResponse firstDayResponse = actionItemService.getEventActionItems(
                userId,
                eventId,
                "2026-08-09"
        );
        EventActionItemResponse thirdDayResponse = actionItemService.getEventActionItems(
                userId,
                eventId,
                "2026-08-11"
        );

        assertThat(firstDayResponse.items()).hasSize(1);
        assertThat(thirdDayResponse.items()).hasSize(1);
        verify(actionItemsRepository, never())
                .findAllByParentEvent_EventIdAndOccurrenceDateAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        eventId,
                        LocalDate.of(2026, 8, 11)
                );
    }
}
