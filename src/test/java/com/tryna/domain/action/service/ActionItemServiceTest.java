package com.tryna.domain.action.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tryna.domain.action.dto.ActionItemStatusUpdateRequest;
import com.tryna.domain.action.dto.ActionItemStatusUpdateResponse;
import com.tryna.domain.action.dto.EventActionItemResponse;
import com.tryna.domain.action.entity.ActionItemOccurrenceStates;
import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.action.repository.ActionItemOccurrenceStatesRepository;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.recommendation.repository.RecommendationFeedbacksRepository;
import com.tryna.domain.reminder.service.AlarmReminderScheduleService;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.ActionErrorCode;
import com.tryna.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionItemServiceTest {

    private ActionItemsRepository actionItemsRepository;
    private ActionItemOccurrenceStatesRepository actionItemOccurrenceStatesRepository;
    private EventsRepository eventsRepository;
    private UserEventsRepository userEventsRepository;
    private ActionItemService actionItemService;

    @BeforeEach
    void setUp() {
        actionItemsRepository = mock(ActionItemsRepository.class);
        actionItemOccurrenceStatesRepository = mock(ActionItemOccurrenceStatesRepository.class);
        eventsRepository = mock(EventsRepository.class);
        userEventsRepository = mock(UserEventsRepository.class);
        actionItemService = new ActionItemService(
                actionItemsRepository,
                actionItemOccurrenceStatesRepository,
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

    @Test
    void recurringMultiDayEventReturnsItemsForDateInsideOccurrence() {
        Long userId = 1L;
        Long eventId = 20L;
        Long templateActionItemId = 100L;
        Long occurrenceActionItemId = 101L;
        Events event = recurringTwoDayWeeklyEvent();
        ActionItems templateActionItem = mock(ActionItems.class);
        ActionItems occurrenceActionItem = mock(ActionItems.class);

        when(templateActionItem.getActionItemId()).thenReturn(templateActionItemId);
        when(templateActionItem.getItemType()).thenReturn(ItemType.TIMED_ACTION);
        when(templateActionItem.getOffsetDays()).thenReturn(0);
        when(occurrenceActionItem.getActionItemId()).thenReturn(occurrenceActionItemId);
        when(occurrenceActionItem.getItemType()).thenReturn(ItemType.UNTIMED_PREP);
        when(occurrenceActionItem.getOccurrenceDate()).thenReturn(LocalDate.of(2026, 11, 16));
        when(eventsRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)).thenReturn(true);
        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(eventId))
                .thenReturn(List.of(templateActionItem, occurrenceActionItem));
        when(actionItemOccurrenceStatesRepository
                .findByActionItem_ActionItemIdInAndOccurrenceDate(
                        List.of(templateActionItemId, occurrenceActionItemId),
                        LocalDate.of(2026, 11, 16)
                ))
                .thenReturn(List.of());

        EventActionItemResponse response = actionItemService.getEventActionItems(
                userId,
                eventId,
                "2026-11-17"
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().occurrenceDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(response.items().getFirst().displayDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(response.items().getFirst().actionItemStatus()).isEqualTo(ActionItemStatus.PENDING);
        assertThat(response.items().get(1).occurrenceDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(response.items().get(1).displayDate()).isNull();
        verify(actionItemOccurrenceStatesRepository)
                .findByActionItem_ActionItemIdInAndOccurrenceDate(
                        List.of(templateActionItemId, occurrenceActionItemId),
                        LocalDate.of(2026, 11, 16)
                );
    }

    @Test
    void recurringMultiDayEventRejectsDateOutsideOccurrence() {
        Long userId = 1L;
        Long eventId = 20L;
        Events event = recurringTwoDayWeeklyEvent();

        when(eventsRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)).thenReturn(true);

        assertThatThrownBy(() -> actionItemService.getEventActionItems(
                userId,
                eventId,
                "2026-11-18"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ActionErrorCode.F103_ACTION_ITEM_400)
        );
    }

    @Test
    void recurringMultiDayStatusUpdateUsesOccurrenceStartDate() {
        Long userId = 1L;
        Long eventId = 20L;
        Long actionItemId = 100L;
        Events event = recurringTwoDayWeeklyEvent();
        ActionItems actionItem = mock(ActionItems.class);
        ActionItemOccurrenceStates state = mock(ActionItemOccurrenceStates.class);

        when(actionItem.getActionItemId()).thenReturn(actionItemId);
        when(actionItem.getParentEvent()).thenReturn(event);
        when(event.getEventId()).thenReturn(eventId);
        when(actionItemsRepository.findByActionItemIdAndDeletedAtIsNull(actionItemId))
                .thenReturn(Optional.of(actionItem));
        when(userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)).thenReturn(true);
        when(actionItemOccurrenceStatesRepository.insertPendingStateIfAbsent(
                actionItemId,
                LocalDate.of(2026, 11, 16)
        )).thenReturn(1);
        when(actionItemOccurrenceStatesRepository.findByActionItem_ActionItemIdAndOccurrenceDate(
                actionItemId,
                LocalDate.of(2026, 11, 16)
        )).thenReturn(Optional.of(state));
        when(state.getActionItemStatus()).thenReturn(ActionItemStatus.COMPLETED);
        when(actionItemOccurrenceStatesRepository.save(state)).thenReturn(state);

        ActionItemStatusUpdateResponse response = actionItemService.updateActionItemStatus(
                userId,
                actionItemId,
                new ActionItemStatusUpdateRequest(
                        LocalDate.of(2026, 11, 17),
                        ActionItemStatus.COMPLETED
                )
        );

        assertThat(response.occurrenceDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(response.actionItemStatus()).isEqualTo(ActionItemStatus.COMPLETED);
        verify(state).updateStatus(ActionItemStatus.COMPLETED);
        verify(actionItemOccurrenceStatesRepository).insertPendingStateIfAbsent(
                actionItemId,
                LocalDate.of(2026, 11, 16)
        );
    }

    @Test
    void recurringMultiDayStatusUpdateRejectsDateOutsideOccurrence() {
        Long userId = 1L;
        Long eventId = 20L;
        Long actionItemId = 100L;
        Events event = recurringTwoDayWeeklyEvent();
        ActionItems actionItem = mock(ActionItems.class);

        when(actionItem.getParentEvent()).thenReturn(event);
        when(event.getEventId()).thenReturn(eventId);
        when(actionItemsRepository.findByActionItemIdAndDeletedAtIsNull(actionItemId))
                .thenReturn(Optional.of(actionItem));
        when(userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)).thenReturn(true);

        assertThatThrownBy(() -> actionItemService.updateActionItemStatus(
                userId,
                actionItemId,
                new ActionItemStatusUpdateRequest(
                        LocalDate.of(2026, 11, 18),
                        ActionItemStatus.COMPLETED
                )
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ActionErrorCode.E106_ACTION_ITEM_400)
        );
    }

    private Events recurringTwoDayWeeklyEvent() {
        Events event = mock(Events.class);
        when(event.getIsRecurring()).thenReturn(true);
        when(event.getStartDate()).thenReturn(LocalDate.of(2026, 11, 9));
        when(event.getEndDate()).thenReturn(LocalDate.of(2026, 11, 10));
        when(event.getRecurrenceType()).thenReturn(RecurrenceType.WEEKLY);
        when(event.getRecurrenceInterval()).thenReturn(1);
        when(event.getRecurrenceDayOfWeek()).thenReturn(RecurrenceDayOfWeek.MON);
        return event;
    }
}
