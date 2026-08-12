package com.tryna.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.entity.ActionItemOccurrenceStates;
import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.action.repository.ActionItemOccurrenceStatesRepository;
import com.tryna.domain.event.dto.EventUpdateRequest;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.UpdateScope;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.RecurringEventExceptionsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.EventErrorCode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class EventUpdateServiceTest {

    private EventUpdateService eventUpdateService;
    private ActionItemsRepository actionItemsRepository;
    private ActionItemOccurrenceStatesRepository actionItemOccurrenceStatesRepository;

    @BeforeEach
    void setUp() {
        actionItemsRepository = mock(ActionItemsRepository.class);
        actionItemOccurrenceStatesRepository = mock(ActionItemOccurrenceStatesRepository.class);
        eventUpdateService = new EventUpdateService(
                mock(EventsRepository.class),
                mock(UserEventsRepository.class),
                mock(LabelsRepository.class),
                actionItemsRepository,
                actionItemOccurrenceStatesRepository,
                mock(RecurringEventExceptionsRepository.class)
        );
    }

    @Test
    void splitOccurrencePreservesOriginalEventDurationWhenEndDateIsOmitted() {
        Events sourceEvent = weeklyRecurringMultiDayEvent();

        LocalDate resolvedEndDate = (LocalDate) invokePrivate(
                "resolveSplitEndDate",
                new Class<?>[]{Events.class, LocalDate.class, LocalDate.class},
                sourceEvent,
                LocalDate.of(2026, 8, 21),
                null
        );

        assertThat(resolvedEndDate).isEqualTo(LocalDate.of(2026, 8, 22));
    }

    @Test
    void splitOccurrenceUsesExplicitEndDateBeforeDurationFallback() {
        Events sourceEvent = weeklyRecurringMultiDayEvent();

        LocalDate resolvedEndDate = (LocalDate) invokePrivate(
                "resolveSplitEndDate",
                new Class<?>[]{Events.class, LocalDate.class, LocalDate.class},
                sourceEvent,
                LocalDate.of(2026, 8, 21),
                LocalDate.of(2026, 8, 24)
        );

        assertThat(resolvedEndDate).isEqualTo(LocalDate.of(2026, 8, 24));
    }

    @Test
    void splitOccurrenceKeepsNullEndDateForOriginalSingleDayEvent() {
        Events sourceEvent = mock(Events.class);
        when(sourceEvent.getStartDate()).thenReturn(LocalDate.of(2026, 8, 10));
        when(sourceEvent.getEndDate()).thenReturn(null);

        LocalDate resolvedEndDate = (LocalDate) invokePrivate(
                "resolveSplitEndDate",
                new Class<?>[]{Events.class, LocalDate.class, LocalDate.class},
                sourceEvent,
                LocalDate.of(2026, 8, 17),
                null
        );

        assertThat(resolvedEndDate).isNull();
    }

    @Test
    void splitOccurrenceSelectsTemplateAndRequestedOccurrenceItemsOnly() {
        LocalDate requestedOccurrence = LocalDate.of(2026, 8, 21);
        ActionItems templateItem = actionItem(
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 13),
                -1
        );
        ActionItems requestedOccurrenceItem = actionItem(
                requestedOccurrence,
                null,
                null
        );
        ActionItems otherOccurrenceItem = actionItem(
                LocalDate.of(2026, 8, 28),
                null,
                null
        );

        assertThat(invokeIsTemplateOrOccurrenceItem(templateItem, requestedOccurrence)).isTrue();
        assertThat(invokeIsTemplateOrOccurrenceItem(requestedOccurrenceItem, requestedOccurrence)).isTrue();
        assertThat(invokeIsTemplateOrOccurrenceItem(otherOccurrenceItem, requestedOccurrence)).isFalse();
    }

    @Test
    void splitOccurrenceCopiesTemplateAndRequestedOccurrenceItemsOnly() {
        Events sourceEvent = weeklyRecurringMultiDayEvent();
        Events targetEvent = nonRecurringMultiDayEvent();
        LocalDate requestedOccurrence = LocalDate.of(2026, 8, 21);
        List<ActionItems> sourceItems = List.of(
                actionItem(LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 13), -1),
                actionItem(requestedOccurrence, null, null),
                actionItem(LocalDate.of(2026, 8, 28), null, null)
        );

        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        sourceEvent.getEventId()
                ))
                .thenReturn(sourceItems);

        Object copiedResult = invokePrivate(
                "copyLinkedActionItems",
                new Class<?>[]{Events.class, Events.class, LocalDate.class, LocalDate.class},
                sourceEvent,
                targetEvent,
                requestedOccurrence,
                requestedOccurrence
        );

        assertThat(readRecordValue(copiedResult, "copiedActionItemCount")).isEqualTo(2);
        verify(actionItemsRepository).softDeleteOccurrenceSpecificItems(
                eq(sourceEvent.getEventId()),
                eq(requestedOccurrence),
                any(LocalDateTime.class)
        );
    }

    @Test
    void thisAndFutureCopyPreservesSkippedSourceOccurrenceGap() {
        Events sourceEvent = weeklyRecurringMultiDayEvent();
        Events targetEvent = weeklyRecurringEvent(1);
        LocalDate sourceOccurrenceDate = LocalDate.of(2026, 8, 21);
        LocalDate targetSeriesStartDate = LocalDate.of(2026, 8, 24);
        List<ActionItems> occurrenceItems = List.of(
                actionItem(sourceOccurrenceDate, null, null),
                actionItem(LocalDate.of(2026, 9, 4), null, null)
        );

        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        sourceEvent.getEventId()
                ))
                .thenReturn(List.of());
        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndOccurrenceDateGreaterThanEqualAndDeletedAtIsNullOrderByOccurrenceDateAscActionItemIdAsc(
                        sourceEvent.getEventId(),
                        sourceOccurrenceDate
                ))
                .thenReturn(occurrenceItems);

        invokePrivate(
                "copyLinkedActionItemsFromOccurrence",
                new Class<?>[]{Events.class, Events.class, LocalDate.class, LocalDate.class},
                sourceEvent,
                targetEvent,
                sourceOccurrenceDate,
                targetSeriesStartDate
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionItems>> copiedItemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionItemsRepository).saveAll(copiedItemsCaptor.capture());

        assertThat(copiedItemsCaptor.getValue())
                .extracting(ActionItems::getOccurrenceDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 9, 7)
                );
    }

    @Test
    void singleOccurrenceCopyRestoresStoredCompletedStatus() {
        Events sourceEvent = weeklyRecurringMultiDayEvent();
        Events targetEvent = nonRecurringMultiDayEvent();
        LocalDate occurrenceDate = LocalDate.of(2026, 8, 21);
        ActionItems sourceItem = actionItem(occurrenceDate, null, null);
        ReflectionTestUtils.setField(sourceItem, "actionItemId", 10L);
        ActionItemOccurrenceStates sourceState =
                ActionItemOccurrenceStates.create(sourceItem, occurrenceDate);
        sourceState.updateStatus(ActionItemStatus.COMPLETED);

        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        sourceEvent.getEventId()
                ))
                .thenReturn(List.of(sourceItem));
        when(actionItemOccurrenceStatesRepository
                .findByActionItem_ActionItemIdInAndOccurrenceDate(List.of(10L), occurrenceDate))
                .thenReturn(List.of(sourceState));

        invokePrivate(
                "copyLinkedActionItems",
                new Class<?>[]{Events.class, Events.class, LocalDate.class, LocalDate.class},
                sourceEvent,
                targetEvent,
                occurrenceDate,
                occurrenceDate
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionItems>> copiedItemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionItemsRepository).saveAll(copiedItemsCaptor.capture());

        ActionItems copiedItem = copiedItemsCaptor.getValue().getFirst();
        assertThat(copiedItem.getActionItemStatus()).isEqualTo(ActionItemStatus.COMPLETED);
        assertThat(copiedItem.getCompletedAt()).isEqualTo(sourceState.getCompletedAt());
    }

    @Test
    void thisAndFutureCopyMovesStoredStatusesToMappedOccurrences() {
        Events sourceEvent = weeklyRecurringMultiDayEvent();
        Events targetEvent = weeklyRecurringEvent(1);
        LocalDate sourceOccurrenceDate = LocalDate.of(2026, 8, 21);
        LocalDate targetOccurrenceDate = LocalDate.of(2026, 8, 24);
        ActionItems sourceItem = actionItem(sourceOccurrenceDate, LocalDate.of(2026, 8, 20), -1);
        ReflectionTestUtils.setField(sourceItem, "actionItemId", 11L);
        ActionItemOccurrenceStates firstState =
                ActionItemOccurrenceStates.create(sourceItem, sourceOccurrenceDate);
        firstState.updateStatus(ActionItemStatus.COMPLETED);
        ActionItemOccurrenceStates thirdState =
                ActionItemOccurrenceStates.create(sourceItem, LocalDate.of(2026, 9, 4));
        thirdState.updateStatus(ActionItemStatus.COMPLETED);

        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        sourceEvent.getEventId()
                ))
                .thenReturn(List.of(sourceItem));
        when(actionItemsRepository
                .findAllByParentEvent_EventIdAndOccurrenceDateGreaterThanEqualAndDeletedAtIsNullOrderByOccurrenceDateAscActionItemIdAsc(
                        sourceEvent.getEventId(),
                        sourceOccurrenceDate
                ))
                .thenReturn(List.of());
        when(actionItemOccurrenceStatesRepository
                .findByActionItem_ActionItemIdInAndOccurrenceDateGreaterThanEqual(
                        List.of(11L),
                        sourceOccurrenceDate
                ))
                .thenReturn(List.of(firstState, thirdState));

        invokePrivate(
                "copyLinkedActionItemsFromOccurrence",
                new Class<?>[]{Events.class, Events.class, LocalDate.class, LocalDate.class},
                sourceEvent,
                targetEvent,
                sourceOccurrenceDate,
                targetOccurrenceDate
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionItemOccurrenceStates>> statesCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionItemOccurrenceStatesRepository).saveAll(statesCaptor.capture());

        assertThat(statesCaptor.getValue())
                .extracting(ActionItemOccurrenceStates::getOccurrenceDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 9, 7)
                );
        assertThat(statesCaptor.getValue())
                .extracting(ActionItemOccurrenceStates::getActionItemStatus)
                .containsOnly(ActionItemStatus.COMPLETED);
    }

    @Test
    void recurringSplitStoresRequestedCompletedStatusAsOccurrenceState() {
        Events targetEvent = weeklyRecurringEvent(1);
        LocalDate occurrenceDate = LocalDate.of(2026, 8, 17);
        EventUpdateRequest.Item completedItem = new EventUpdateRequest.Item(
                20L,
                "완료된 준비 항목",
                ItemType.UNTIMED_PREP,
                occurrenceDate,
                null,
                null,
                null,
                ActionItemStatus.COMPLETED,
                CreatedBy.USER,
                null
        );
        EventUpdateRequest.ActionItems actionItems = new EventUpdateRequest.ActionItems(
                List.of(completedItem),
                List.of()
        );

        invokePrivate(
                "syncActionItems",
                new Class<?>[]{Events.class, EventUpdateRequest.ActionItems.class, boolean.class},
                targetEvent,
                actionItems,
                true
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionItemOccurrenceStates>> statesCaptor = ArgumentCaptor.forClass(List.class);
        verify(actionItemOccurrenceStatesRepository).saveAll(statesCaptor.capture());

        ActionItemOccurrenceStates savedState = statesCaptor.getValue().getFirst();
        assertThat(savedState.getOccurrenceDate()).isEqualTo(occurrenceDate);
        assertThat(savedState.getActionItemStatus()).isEqualTo(ActionItemStatus.COMPLETED);
        assertThat(savedState.getCompletedAt()).isNotNull();
    }

    @Test
    void singleOccurrenceUpdateAcceptsOmittedRecurrenceFields() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(null, null, null, null);

        assertThatCode(() -> invokeValidateSingleOccurrenceRecurrenceRequest(sourceEvent, request))
                .doesNotThrowAnyException();
    }

    @Test
    void singleOccurrenceUpdateAcceptsSameRecurrenceFields() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(true, RecurrenceType.WEEKLY, 2, "2026-09-30");

        assertThatCode(() -> invokeValidateSingleOccurrenceRecurrenceRequest(sourceEvent, request))
                .doesNotThrowAnyException();
    }

    @Test
    void singleOccurrenceUpdateRejectsRecurrenceConversion() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(true, RecurrenceType.MONTHLY, 2, "2026-09-30");

        assertC107BadRequest(() -> invokeValidateSingleOccurrenceRecurrenceRequest(sourceEvent, request));
    }

    @Test
    void singleOccurrenceUpdateRejectsNoneRecurrenceRequest() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(false, RecurrenceType.NONE, null, null);

        assertC107BadRequest(() -> invokeValidateSingleOccurrenceRecurrenceRequest(sourceEvent, request));
    }

    @Test
    void singleOccurrenceUpdateRejectsIntervalOnlyChange() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(null, null, 1, null);

        assertC107BadRequest(() -> invokeValidateSingleOccurrenceRecurrenceRequest(sourceEvent, request));
    }

    @Test
    void preserveExistingRecurrenceRuleUsesEventStartDateWhenRequestStartDateIsNull() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(null, null, null, null);

        Object recurrenceRule = invokeResolveUpdateRecurrenceRule(
                sourceEvent,
                request,
                null,
                sourceEvent.getRecurrenceEndDate()
        );

        assertThat(readRecordValue(recurrenceRule, "isRecurring")).isEqualTo(true);
        assertThat(readRecordValue(recurrenceRule, "recurrenceType")).isEqualTo(RecurrenceType.WEEKLY);
        assertThat(readRecordValue(recurrenceRule, "recurrenceInterval")).isEqualTo(2);
        assertThat(readRecordValue(recurrenceRule, "recurrenceDayOfWeek")).isEqualTo(RecurrenceDayOfWeek.MON);
    }

    @Test
    void recurrenceTypeChangeReusesExistingIntervalWhenIntervalIsOmitted() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(true, RecurrenceType.MONTHLY, null, "2026-09-30");

        Object recurrenceRule = invokeResolveUpdateRecurrenceRule(
                sourceEvent,
                request,
                LocalDate.of(2026, 8, 17),
                sourceEvent.getRecurrenceEndDate()
        );

        assertThat(readRecordValue(recurrenceRule, "recurrenceType")).isEqualTo(RecurrenceType.MONTHLY);
        assertThat(readRecordValue(recurrenceRule, "recurrenceInterval")).isEqualTo(2);
        assertThat(readRecordValue(recurrenceRule, "recurrenceDayOfMonth")).isEqualTo(17);
    }

    @Test
    void recurrenceEndDateBeforeStartDateIsRejected() {
        Events sourceEvent = weeklyRecurringEvent(2);
        EventUpdateRequest request = updateRequest(true, RecurrenceType.WEEKLY, null, "2026-08-09");

        assertC107BadRequest(() -> invokeResolveUpdateRecurrenceRule(
                sourceEvent,
                request,
                LocalDate.of(2026, 8, 10),
                sourceEvent.getRecurrenceEndDate()
        ));
    }

    @Test
    void actionItemPayloadAcceptsUpdateDeleteAndCreateShape() {
        EventUpdateRequest.Item existingTimedAction = actionItemRequest(
                1L,
                "회의 장소 확인하기",
                ItemType.TIMED_ACTION,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 9),
                LocalDateTime.of(2026, 8, 9, 9, 0)
        );
        EventUpdateRequest.Item newUntimedPrep = actionItemRequest(
                null,
                "자료 준비하기",
                ItemType.UNTIMED_PREP,
                LocalDate.of(2026, 8, 10),
                null,
                null
        );
        EventUpdateRequest.ActionItems actionItems = new EventUpdateRequest.ActionItems(
                List.of(existingTimedAction, newUntimedPrep),
                List.of(2L)
        );

        assertThatCode(() -> invokeValidateActionItemSyncRequest(actionItems))
                .doesNotThrowAnyException();
    }

    @Test
    void timedActionRequiresDisplayDate() {
        EventUpdateRequest.ActionItems actionItems = new EventUpdateRequest.ActionItems(
                List.of(actionItemRequest(
                        null,
                        "회의 장소 확인하기",
                        ItemType.TIMED_ACTION,
                        LocalDate.of(2026, 8, 10),
                        null,
                        LocalDateTime.of(2026, 8, 9, 9, 0)
                )),
                List.of()
        );

        assertC107BadRequest(() -> invokeValidateActionItemSyncRequest(actionItems));
    }

    @Test
    void untimedPrepRejectsDisplayDateAndDisplayTime() {
        EventUpdateRequest.ActionItems actionItems = new EventUpdateRequest.ActionItems(
                List.of(actionItemRequest(
                        null,
                        "자료 준비하기",
                        ItemType.UNTIMED_PREP,
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 9),
                        LocalDateTime.of(2026, 8, 9, 9, 0)
                )),
                List.of()
        );

        assertC107BadRequest(() -> invokeValidateActionItemSyncRequest(actionItems));
    }

    private Events weeklyRecurringEvent(int interval) {
        return Events.createInternalEvent(
                "매주 회의",
                "매주 회의",
                null,
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 10, 9, 0),
                LocalDate.of(2026, 8, 10),
                LocalDateTime.of(2026, 8, 10, 10, 0),
                false,
                true,
                RecurrenceType.WEEKLY,
                interval,
                RecurrenceDayOfWeek.MON,
                null,
                LocalDate.of(2026, 9, 30).atStartOfDay(),
                "회의실",
                "MEETING",
                EventStatus.CONFIRMED
        );
    }

    private Events weeklyRecurringMultiDayEvent() {
        return Events.createInternalEvent(
                "데모데이 부산",
                "데모데이 부산",
                null,
                LocalDate.of(2026, 8, 14),
                LocalDateTime.of(2026, 8, 14, 9, 0),
                LocalDate.of(2026, 8, 15),
                LocalDateTime.of(2026, 8, 15, 18, 0),
                false,
                true,
                RecurrenceType.WEEKLY,
                1,
                RecurrenceDayOfWeek.FRI,
                null,
                LocalDate.of(2026, 9, 30).atStartOfDay(),
                "부산",
                "EVENT",
                EventStatus.CONFIRMED
        );
    }

    private Events nonRecurringMultiDayEvent() {
        return Events.createInternalEvent(
                "수정된 데모데이 부산",
                "수정된 데모데이 부산",
                null,
                LocalDate.of(2026, 8, 21),
                LocalDateTime.of(2026, 8, 21, 9, 0),
                LocalDate.of(2026, 8, 22),
                LocalDateTime.of(2026, 8, 22, 18, 0),
                false,
                false,
                RecurrenceType.NONE,
                1,
                RecurrenceDayOfWeek.NONE,
                null,
                null,
                "부산",
                "EVENT",
                EventStatus.CONFIRMED
        );
    }

    private ActionItems actionItem(
            LocalDate occurrenceDate,
            LocalDate displayDate,
            Integer offsetDays
    ) {
        ItemType itemType = offsetDays == null ? ItemType.UNTIMED_PREP : ItemType.TIMED_ACTION;
        return ActionItems.create(
                weeklyRecurringMultiDayEvent(),
                "준비 항목",
                itemType,
                occurrenceDate,
                displayDate,
                displayDate == null ? null : displayDate.atTime(9, 0),
                offsetDays,
                CreatedBy.SYSTEM,
                "template-1"
        );
    }

    private boolean invokeIsTemplateOrOccurrenceItem(
            ActionItems actionItem,
            LocalDate occurrenceDate
    ) {
        return (boolean) invokePrivate(
                "isTemplateOrOccurrenceItem",
                new Class<?>[]{ActionItems.class, LocalDate.class},
                actionItem,
                occurrenceDate
        );
    }

    private EventUpdateRequest updateRequest(
            Boolean isRecurring,
            RecurrenceType recurrenceType,
            Integer recurrenceInterval,
            String recurrenceEndDate
    ) {
        return new EventUpdateRequest(
                "수정 회의",
                null,
                "2026-08-10",
                "09:00",
                "2026-08-10",
                "10:00",
                false,
                "회의실",
                null,
                isRecurring,
                recurrenceType,
                recurrenceInterval,
                recurrenceEndDate,
                "2026-08-10",
                UpdateScope.SINGLE,
                null
        );
    }

    private EventUpdateRequest.Item actionItemRequest(
            Long actionItemId,
            String title,
            ItemType itemType,
            LocalDate occurrenceDate,
            LocalDate displayDate,
            LocalDateTime displayTime
    ) {
        return new EventUpdateRequest.Item(
                actionItemId,
                title,
                itemType,
                occurrenceDate,
                displayDate,
                displayTime,
                -1,
                ActionItemStatus.PENDING,
                CreatedBy.USER,
                null
        );
    }

    private void invokeValidateSingleOccurrenceRecurrenceRequest(
            Events sourceEvent,
            EventUpdateRequest request
    ) {
        invokePrivate(
                "validateSingleOccurrenceRecurrenceRequest",
                new Class<?>[]{Events.class, EventUpdateRequest.class},
                sourceEvent,
                request
        );
    }

    private void invokeValidateActionItemSyncRequest(EventUpdateRequest.ActionItems actionItems) {
        invokePrivate(
                "validateActionItemSyncRequest",
                new Class<?>[]{List.class, List.class},
                actionItems.items(),
                actionItems.deletedActionItemIds()
        );
    }

    private Object invokeResolveUpdateRecurrenceRule(
            Events sourceEvent,
            EventUpdateRequest request,
            LocalDate startDate,
            LocalDateTime fallbackRecurrenceEndDate
    ) {
        return invokePrivate(
                "resolveUpdateRecurrenceRule",
                new Class<?>[]{Events.class, EventUpdateRequest.class, LocalDate.class, LocalDateTime.class},
                sourceEvent,
                request,
                startDate,
                fallbackRecurrenceEndDate
        );
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = EventUpdateService.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(eventUpdateService, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Object readRecordValue(Object record, String accessorName) {
        try {
            Method accessor = record.getClass().getDeclaredMethod(accessorName);
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertC107BadRequest(ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.C107_EVENT_UPDATE_400);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
