package com.tryna.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.action.repository.ActionItemsRepository;
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

class EventUpdateServiceTest {

    private EventUpdateService eventUpdateService;

    @BeforeEach
    void setUp() {
        eventUpdateService = new EventUpdateService(
                mock(EventsRepository.class),
                mock(UserEventsRepository.class),
                mock(LabelsRepository.class),
                mock(ActionItemsRepository.class),
                mock(RecurringEventExceptionsRepository.class)
        );
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
