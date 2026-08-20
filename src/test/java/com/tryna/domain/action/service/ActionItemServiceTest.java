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
import com.tryna.domain.action.dto.MonthlyTimedActionItemResponse;
import com.tryna.domain.action.entity.ActionItemOccurrenceStates;
import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.action.repository.ActionItemOccurrenceStatesRepository;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
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
import java.time.LocalDateTime;
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

    @Test
    void monthlyTimedActionItemsIncludeDirectItemsOnDisplayDate() {
        Long userId = 1L;
        ActionItems actionItem = mock(ActionItems.class);
        ActionItems deletedActionItem = mock(ActionItems.class);
        Events event = mock(Events.class);
        LocalDate displayDate = LocalDate.of(2026, 8, 30);
        LocalDate parentStartDate = LocalDate.of(2026, 9, 2);

        when(actionItem.getActionItemId()).thenReturn(200L);
        when(actionItem.getParentEvent()).thenReturn(event);
        when(actionItem.getTitle()).thenReturn("선물 구매하기");
        when(actionItem.getItemType()).thenReturn(ItemType.TIMED_ACTION);
        when(actionItem.getDisplayDate()).thenReturn(displayDate);
        when(actionItem.getDisplayDatetime()).thenReturn(displayDate.atTime(18, 0));
        when(actionItem.getActionItemStatus()).thenReturn(ActionItemStatus.PENDING);
        when(actionItem.getCreatedBy()).thenReturn(CreatedBy.USER);
        when(deletedActionItem.getActionItemStatus()).thenReturn(ActionItemStatus.DELETED);
        when(event.getEventId()).thenReturn(10L);
        when(event.getTitle()).thenReturn("지민이 생일");
        when(event.getStartDate()).thenReturn(parentStartDate);
        when(actionItemsRepository.findCalendarActionItemsByDateRange(
                userId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ItemType.TIMED_ACTION,
                List.of(EventStatus.CONFIRMED, EventStatus.NEEDS_CONFIRMATION)
        )).thenReturn(List.of(actionItem, deletedActionItem));
        when(actionItemsRepository.findRecurringTimedActionItemsByUserId(
                userId,
                ItemType.TIMED_ACTION,
                List.of(EventStatus.CONFIRMED, EventStatus.NEEDS_CONFIRMATION)
        )).thenReturn(List.of());
        when(userEventsRepository.findLabelIdsByUserIdAndEventIds(userId, List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 110L}));

        MonthlyTimedActionItemResponse response =
                actionItemService.getMonthlyTimedActionItems(userId, 2026, 8);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(8);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().title()).isEqualTo("선물 구매하기");
        assertThat(response.items().getFirst().displayDate()).isEqualTo(displayDate);
        assertThat(response.items().getFirst().parentOccurrenceDate()).isEqualTo(parentStartDate);
        assertThat(response.items().getFirst().labelId()).isEqualTo(110L);
    }

    @Test
    void monthlyTimedActionItemsCalculateRecurringOccurrencesAndApplyOccurrenceState() {
        Long userId = 1L;
        Long actionItemId = 300L;
        Events event = mock(Events.class);
        ActionItems actionItem = mock(ActionItems.class);
        ActionItemOccurrenceStates state = mock(ActionItemOccurrenceStates.class);
        ActionItemOccurrenceStates deletedState = mock(ActionItemOccurrenceStates.class);
        LocalDate completedOccurrenceDate = LocalDate.of(2026, 8, 10);
        LocalDate deletedOccurrenceDate = LocalDate.of(2026, 8, 17);

        when(event.getEventId()).thenReturn(20L);
        when(event.getTitle()).thenReturn("매주 운동");
        when(event.getIsRecurring()).thenReturn(true);
        when(event.getStartDate()).thenReturn(LocalDate.of(2026, 8, 3));
        when(event.getRecurrenceType()).thenReturn(RecurrenceType.WEEKLY);
        when(event.getRecurrenceInterval()).thenReturn(1);
        when(event.getRecurrenceDayOfWeek()).thenReturn(RecurrenceDayOfWeek.MON);
        when(actionItem.getActionItemId()).thenReturn(actionItemId);
        when(actionItem.getParentEvent()).thenReturn(event);
        when(actionItem.getTitle()).thenReturn("운동복 준비하기");
        when(actionItem.getItemType()).thenReturn(ItemType.TIMED_ACTION);
        when(actionItem.getOffsetDays()).thenReturn(-1);
        when(actionItem.getDisplayDatetime()).thenReturn(LocalDateTime.of(2026, 8, 2, 8, 0));
        when(actionItem.getCreatedBy()).thenReturn(CreatedBy.SYSTEM);
        when(actionItemsRepository.findCalendarActionItemsByDateRange(
                userId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ItemType.TIMED_ACTION,
                List.of(EventStatus.CONFIRMED, EventStatus.NEEDS_CONFIRMATION)
        )).thenReturn(List.of());
        when(actionItemsRepository.findRecurringTimedActionItemsByUserId(
                userId,
                ItemType.TIMED_ACTION,
                List.of(EventStatus.CONFIRMED, EventStatus.NEEDS_CONFIRMATION)
        )).thenReturn(List.of(actionItem));
        when(actionItemOccurrenceStatesRepository
                .findByActionItem_ActionItemIdInAndOccurrenceDateIn(
                        List.of(actionItemId),
                        List.of(
                                LocalDate.of(2026, 8, 3),
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 17),
                                LocalDate.of(2026, 8, 24),
                                LocalDate.of(2026, 8, 31)
                        )
                )).thenReturn(List.of(state, deletedState));
        when(state.getActionItem()).thenReturn(actionItem);
        when(state.getOccurrenceDate()).thenReturn(completedOccurrenceDate);
        when(state.getActionItemStatus()).thenReturn(ActionItemStatus.COMPLETED);
        when(deletedState.getActionItem()).thenReturn(actionItem);
        when(deletedState.getOccurrenceDate()).thenReturn(deletedOccurrenceDate);
        when(deletedState.getActionItemStatus()).thenReturn(ActionItemStatus.DELETED);
        when(userEventsRepository.findLabelIdsByUserIdAndEventIds(userId, List.of(20L)))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 120L}));

        MonthlyTimedActionItemResponse response =
                actionItemService.getMonthlyTimedActionItems(userId, 2026, 8);

        assertThat(response.items()).hasSize(4);
        assertThat(response.items())
                .noneMatch(item -> item.parentOccurrenceDate().equals(deletedOccurrenceDate));
        assertThat(response.items())
                .filteredOn(item -> item.parentOccurrenceDate().equals(completedOccurrenceDate))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.displayDate()).isEqualTo(LocalDate.of(2026, 8, 9));
                    assertThat(item.actionItemStatus()).isEqualTo(ActionItemStatus.COMPLETED);
                    assertThat(item.labelId()).isEqualTo(120L);
                });
        assertThat(response.items())
                .filteredOn(item -> item.parentOccurrenceDate().equals(LocalDate.of(2026, 8, 24)))
                .singleElement()
                .satisfies(item -> assertThat(item.actionItemStatus()).isEqualTo(ActionItemStatus.PENDING));
    }

    @Test
    void monthlyTimedActionItemsReturnEmptyItemsWhenNoMatches() {
        Long userId = 1L;

        when(actionItemsRepository.findCalendarActionItemsByDateRange(
                userId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ItemType.TIMED_ACTION,
                List.of(EventStatus.CONFIRMED, EventStatus.NEEDS_CONFIRMATION)
        )).thenReturn(List.of());
        when(actionItemsRepository.findRecurringTimedActionItemsByUserId(
                userId,
                ItemType.TIMED_ACTION,
                List.of(EventStatus.CONFIRMED, EventStatus.NEEDS_CONFIRMATION)
        )).thenReturn(List.of());

        MonthlyTimedActionItemResponse response =
                actionItemService.getMonthlyTimedActionItems(userId, 2026, 8);

        assertThat(response.items()).isEmpty();
        verify(userEventsRepository, never()).findLabelIdsByUserIdAndEventIds(userId, List.of());
    }

    @Test
    void monthlyTimedActionItemsRejectInvalidYearMonth() {
        assertThatThrownBy(() -> actionItemService.getMonthlyTimedActionItems(1L, 2026, 13))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ActionErrorCode.F104_ACTION_ITEM_400)
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
