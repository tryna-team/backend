package com.tryna.domain.event.service;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.dto.EventUpdateRequest;
import com.tryna.domain.event.dto.EventUpdateResponse;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.RecurringEventExceptionType;
import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.event.enums.UpdateScope;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.RecurringEventExceptionsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import com.tryna.global.exception.EventErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventUpdateService {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_LOCATION_LENGTH = 255;
    private static final Set<EventStatus> UPDATABLE_STATUSES = Set.of(
            EventStatus.CONFIRMED,
            EventStatus.NEEDS_CONFIRMATION
    );

    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final LabelsRepository labelsRepository;
    private final ActionItemsRepository actionItemsRepository;
    private final RecurringEventExceptionsRepository recurringEventExceptionsRepository;

    @Transactional
    public EventUpdateResponse updateEvent(
            Long userId,
            Long eventId,
            EventUpdateRequest request
    ) {
        validateRequest(request);

        Events event = findUpdatableEvent(userId, eventId);
        validateInternalOwnerEvent(userId, event);

        UserEvents userEvent = userEventsRepository.findByUser_UserIdAndEvent_EventId(userId, eventId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_403));
        Labels label = resolveLabel(userId, request.labelId(), userEvent.getLabel());

        if (Boolean.TRUE.equals(event.getIsRecurring())) {
            return updateRecurringEvent(event, userEvent, label, request);
        }

        if (request.updateScope() != UpdateScope.SINGLE) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        LocalDate previousStartDate = event.getStartDate();
        LocalDate startDate = parseDate(request.startDate());
        LocalTime startTime = parseTime(request.startTime());
        LocalDate endDate = parseDate(request.endDate());
        LocalTime endTime = parseTime(request.endTime());
        boolean isAllDay = resolveAllDay(request.isAllDay(), startTime);
        validateTimePolicy(startDate, startTime, endDate, endTime, isAllDay);

        EventStatus status = startDate == null
                ? EventStatus.NEEDS_CONFIRMATION
                : EventStatus.CONFIRMED;

        event.updateInternalEvent(
                request.eventTitle().trim(),
                normalizeBlank(request.description()),
                startDate,
                combine(startDate, startTime),
                endDate,
                combine(endDate, endTime),
                isAllDay,
                normalizeBlank(request.location()),
                status
        );
        userEvent.changeLabel(label);

        ActionItemAdjustmentResult adjustmentResult =
                adjustLinkedActionItems(eventId, previousStartDate, startDate);

        eventsRepository.flush();

        return new EventUpdateResponse(
                event.getEventId(),
                UpdateScope.SINGLE,
                event.getEventStatus(),
                1,
                adjustmentResult.adjustedActionItemCount(),
                adjustmentResult.requiresActionItemReview(),
                resolveLabelId(label),
                event.getUpdatedAt()
        );
    }

    private void validateRequest(EventUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        validateRequiredText(request.eventTitle());
        validateTitleLength(request.eventTitle());
        validateLocationLength(request.location());

        if (request.updateScope() == null) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private Events findUpdatableEvent(Long userId, Long eventId) {
        return eventsRepository.findVisibleEventAccessibleToUser(
                        userId,
                        eventId,
                        UPDATABLE_STATUSES,
                        ConnectionStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(EventErrorCode.C107_EVENT_UPDATE_404));
    }

    private void validateInternalOwnerEvent(Long userId, Events event) {
        if (event.getSourceType() == SourceType.EXTERNAL_CALENDAR) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        if (!userEventsRepository.existsOwnerByUserIdAndEventId(userId, event.getEventId())) {
            throw new BusinessException(CommonErrorCode.COMMON_403);
        }
    }

    private Labels resolveLabel(Long userId, Long requestedLabelId, Labels currentLabel) {
        if (requestedLabelId == null) {
            return currentLabel;
        }

        return findOwnedLabel(userId, requestedLabelId);
    }

    private Labels findOwnedLabel(Long userId, Long labelId) {
        return labelsRepository.findByLabelIdAndUser_UserId(labelId, userId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400));
    }

    private EventUpdateResponse updateRecurringEvent(
            Events event,
            UserEvents userEvent,
            Labels label,
            EventUpdateRequest request
    ) {
        LocalDate occurrenceDate = parseRequiredOccurrenceDate(request.occurrenceDate());
        if (!isRecurringOccurrenceOn(event, occurrenceDate)) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        return switch (request.updateScope()) {
            case SINGLE -> updateSingleRecurringOccurrence(event, userEvent, label, request, occurrenceDate);
            case THIS_AND_FUTURE -> updateThisAndFutureRecurringOccurrences(event, userEvent, label, request, occurrenceDate);
        };
    }

    private EventUpdateResponse updateSingleRecurringOccurrence(
            Events event,
            UserEvents userEvent,
            Labels label,
            EventUpdateRequest request,
            LocalDate occurrenceDate
    ) {
        recurringEventExceptionsRepository.insertDeletedOccurrenceIfAbsent(
                event.getEventId(),
                occurrenceDate,
                RecurringEventExceptionType.DELETED.name()
        );

        Events modifiedEvent = createModifiedEvent(event, request, occurrenceDate, false, null);
        saveNewOwnerEvent(userEvent, modifiedEvent, label);
        int copiedActionItemCount = copyLinkedActionItems(event, modifiedEvent, modifiedEvent.getStartDate());

        eventsRepository.flush();

        return new EventUpdateResponse(
                modifiedEvent.getEventId(),
                UpdateScope.SINGLE,
                modifiedEvent.getEventStatus(),
                1,
                copiedActionItemCount,
                false,
                resolveLabelId(label),
                modifiedEvent.getUpdatedAt()
        );
    }

    private EventUpdateResponse updateThisAndFutureRecurringOccurrences(
            Events event,
            UserEvents userEvent,
            Labels label,
            EventUpdateRequest request,
            LocalDate occurrenceDate
    ) {
        if (occurrenceDate.equals(event.getStartDate())) {
            userEvent.changeLabel(label);
            return updateWholeRecurringSeries(event, request, label);
        }

        LocalDateTime originalRecurrenceEndDate = event.getRecurrenceEndDate();
        event.truncateRecurrenceEndDate(occurrenceDate.minusDays(1).atTime(LocalTime.MAX));

        Events modifiedEvent = createModifiedEvent(event, request, occurrenceDate, true, originalRecurrenceEndDate);
        saveNewOwnerEvent(userEvent, modifiedEvent, label);
        int copiedActionItemCount = copyLinkedActionItems(event, modifiedEvent, modifiedEvent.getStartDate());

        eventsRepository.flush();

        return new EventUpdateResponse(
                modifiedEvent.getEventId(),
                UpdateScope.THIS_AND_FUTURE,
                modifiedEvent.getEventStatus(),
                2,
                copiedActionItemCount,
                false,
                resolveLabelId(label),
                modifiedEvent.getUpdatedAt()
        );
    }

    private EventUpdateResponse updateWholeRecurringSeries(
            Events event,
            EventUpdateRequest request,
            Labels label
    ) {
        LocalDate previousStartDate = event.getStartDate();
        LocalDate startDate = parseDate(request.startDate());
        LocalTime startTime = parseTime(request.startTime());
        LocalDate endDate = parseDate(request.endDate());
        LocalTime endTime = parseTime(request.endTime());
        boolean isAllDay = resolveAllDay(request.isAllDay(), startTime);
        validateTimePolicy(startDate, startTime, endDate, endTime, isAllDay);

        EventStatus status = startDate == null
                ? EventStatus.NEEDS_CONFIRMATION
                : EventStatus.CONFIRMED;

        event.updateInternalEvent(
                request.eventTitle().trim(),
                normalizeBlank(request.description()),
                startDate,
                combine(startDate, startTime),
                endDate,
                combine(endDate, endTime),
                isAllDay,
                normalizeBlank(request.location()),
                status
        );

        ActionItemAdjustmentResult adjustmentResult =
                adjustLinkedActionItems(event.getEventId(), previousStartDate, startDate);

        eventsRepository.flush();

        return new EventUpdateResponse(
                event.getEventId(),
                UpdateScope.THIS_AND_FUTURE,
                event.getEventStatus(),
                1,
                adjustmentResult.adjustedActionItemCount(),
                adjustmentResult.requiresActionItemReview(),
                resolveLabelId(label),
                event.getUpdatedAt()
        );
    }

    private Events createModifiedEvent(
            Events sourceEvent,
            EventUpdateRequest request,
            LocalDate occurrenceDate,
            boolean recurring,
            LocalDateTime recurrenceEndDate
    ) {
        LocalDate startDate = parseDate(request.startDate());
        LocalTime startTime = parseTime(request.startTime());
        LocalDate endDate = parseDate(request.endDate());
        LocalTime endTime = parseTime(request.endTime());
        boolean isAllDay = resolveAllDay(request.isAllDay(), startTime);

        LocalDate resolvedStartDate = startDate == null ? occurrenceDate : startDate;
        LocalDate resolvedEndDate = endDate;
        validateTimePolicy(resolvedStartDate, startTime, resolvedEndDate, endTime, isAllDay);
        EventStatus status = resolvedStartDate == null
                ? EventStatus.NEEDS_CONFIRMATION
                : EventStatus.CONFIRMED;

        return eventsRepository.save(Events.createInternalEvent(
                request.eventTitle().trim(),
                request.eventTitle().trim(),
                normalizeBlank(request.description()),
                resolvedStartDate,
                combine(resolvedStartDate, startTime),
                resolvedEndDate,
                combine(resolvedEndDate, endTime),
                isAllDay,
                recurring,
                recurring ? sourceEvent.getRecurrenceType() : RecurrenceType.NONE,
                recurring ? sourceEvent.getRecurrenceInterval() : null,
                recurring ? resolveUpdatedRecurrenceDayOfWeek(sourceEvent, resolvedStartDate) : RecurrenceDayOfWeek.NONE,
                recurring ? resolveUpdatedRecurrenceDayOfMonth(sourceEvent, resolvedStartDate) : null,
                recurring ? recurrenceEndDate : null,
                normalizeBlank(request.location()),
                sourceEvent.getEventType(),
                SourceType.USER_MANUAL_EDIT,
                status
        ));
    }

    private void saveNewOwnerEvent(UserEvents sourceUserEvent, Events event, Labels label) {
        userEventsRepository.save(UserEvents.createOwner(sourceUserEvent.getUser(), event, label));
    }

    private int copyLinkedActionItems(Events sourceEvent, Events targetEvent, LocalDate targetStartDate) {
        List<ActionItems> sourceActionItems =
                actionItemsRepository.findAllByParentEvent_EventIdAndDeletedAtIsNull(sourceEvent.getEventId());

        if (sourceActionItems.isEmpty()) {
            return 0;
        }

        List<ActionItems> copiedActionItems = new ArrayList<>();
        for (ActionItems sourceActionItem : sourceActionItems) {
            copiedActionItems.add(copyActionItem(sourceActionItem, targetEvent, targetStartDate));
        }

        actionItemsRepository.saveAll(copiedActionItems);
        return copiedActionItems.size();
    }

    private ActionItems copyActionItem(
            ActionItems sourceActionItem,
            Events targetEvent,
            LocalDate targetStartDate
    ) {
        LocalDate displayDate = sourceActionItem.getDisplayDate();
        LocalDateTime displayDatetime = sourceActionItem.getDisplayDatetime();

        if (sourceActionItem.getItemType() == ItemType.TIMED_ACTION
                && sourceActionItem.getOffsetDays() != null
                && targetStartDate != null) {
            displayDate = targetStartDate.plusDays(sourceActionItem.getOffsetDays());
            displayDatetime = displayDatetime == null
                    ? null
                    : LocalDateTime.of(displayDate, displayDatetime.toLocalTime());
        }

        return ActionItems.create(
                targetEvent,
                sourceActionItem.getTitle(),
                sourceActionItem.getItemType(),
                displayDate,
                displayDatetime,
                sourceActionItem.getOffsetDays(),
                sourceActionItem.getCreatedBy(),
                sourceActionItem.getSourceTemplateId()
        );
    }

    private ActionItemAdjustmentResult adjustLinkedActionItems(
            Long eventId,
            LocalDate previousStartDate,
            LocalDate updatedStartDate
    ) {
        if (Objects.equals(previousStartDate, updatedStartDate)) {
            return new ActionItemAdjustmentResult(0, false);
        }

        List<ActionItems> actionItems =
                actionItemsRepository.findAllByParentEvent_EventIdAndDeletedAtIsNull(eventId);

        if (updatedStartDate == null) {
            boolean requiresReview = actionItems.stream()
                    .anyMatch(ActionItems::requiresReviewWhenParentDateMissing);
            return new ActionItemAdjustmentResult(0, requiresReview);
        }

        int adjustedCount = 0;
        for (ActionItems actionItem : actionItems) {
            if (actionItem.adjustDisplayDateByParentStartDate(updatedStartDate)) {
                adjustedCount++;
            }
        }

        return new ActionItemAdjustmentResult(adjustedCount, false);
    }

    private LocalDate parseRequiredOccurrenceDate(String value) {
        LocalDate occurrenceDate = parseDate(value);
        if (occurrenceDate == null) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
        return occurrenceDate;
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
                event.getStartDate().withDayOfMonth(1),
                date.withDayOfMonth(1)
        );
        return months % interval == 0;
    }

    private boolean isYearlyOccurrence(Events event, LocalDate date, int interval) {
        if (event.getRecurrenceDayOfMonth() == null
                || date.getDayOfMonth() != event.getRecurrenceDayOfMonth()
                || date.getMonth() != event.getStartDate().getMonth()) {
            return false;
        }

        return ChronoUnit.YEARS.between(event.getStartDate(), date) % interval == 0;
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

    private RecurrenceDayOfWeek resolveUpdatedRecurrenceDayOfWeek(Events sourceEvent, LocalDate startDate) {
        if (sourceEvent.getRecurrenceType() == RecurrenceType.WEEKLY && startDate != null) {
            return toRecurrenceDayOfWeek(startDate);
        }
        return sourceEvent.getRecurrenceDayOfWeek();
    }

    private Integer resolveUpdatedRecurrenceDayOfMonth(Events sourceEvent, LocalDate startDate) {
        if ((sourceEvent.getRecurrenceType() == RecurrenceType.MONTHLY
                || sourceEvent.getRecurrenceType() == RecurrenceType.YEARLY)
                && startDate != null) {
            return startDate.getDayOfMonth();
        }
        return sourceEvent.getRecurrenceDayOfMonth();
    }

    private Long resolveLabelId(Labels label) {
        return label == null ? null : label.getLabelId();
    }

    private void validateRequiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private void validateTitleLength(String title) {
        if (title.trim().length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private void validateLocationLength(String location) {
        if (location != null && location.trim().length() > MAX_LOCATION_LENGTH) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private boolean resolveAllDay(Boolean isAllDay, LocalTime startTime) {
        if (startTime == null) {
            return true;
        }
        return Boolean.TRUE.equals(isAllDay);
    }

    private void validateTimePolicy(
            LocalDate startDate,
            LocalTime startTime,
            LocalDate endDate,
            LocalTime endTime,
            boolean isAllDay
    ) {
        if (isAllDay && (startTime != null || endTime != null)) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        if (startDate == null && startTime != null) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        if (startDate == null && endDate != null) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        if (endTime != null && endDate == null) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        LocalDateTime startDateTime = combine(startDate, startTime);
        LocalDateTime endDateTime = combine(endDate, endTime);
        if (startDateTime != null && endDateTime != null && endDateTime.isBefore(startDateTime)) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private LocalDateTime combine(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            return null;
        }
        return LocalDateTime.of(date, time);
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ActionItemAdjustmentResult(
            Integer adjustedActionItemCount,
            Boolean requiresActionItemReview
    ) {
    }
}
