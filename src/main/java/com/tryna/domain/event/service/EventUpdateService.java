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
        RecurrenceRule recurrenceRule =
                resolveUpdateRecurrenceRule(event, request, startDate, event.getRecurrenceEndDate());

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
        event.updateRecurrenceRule(
                recurrenceRule.isRecurring(),
                recurrenceRule.recurrenceType(),
                recurrenceRule.recurrenceInterval(),
                recurrenceRule.recurrenceDayOfWeek(),
                recurrenceRule.recurrenceDayOfMonth(),
                recurrenceRule.recurrenceEndDate()
        );
        userEvent.changeLabel(label);

        ActionItemAdjustmentResult adjustmentResult =
                adjustLinkedActionItems(eventId, previousStartDate, startDate);

        eventsRepository.flush();

        return buildUpdateResponse(
                event,
                UpdateScope.SINGLE,
                1,
                adjustmentResult.adjustedActionItemCount(),
                adjustmentResult.requiresActionItemReview(),
                label
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
        validateSingleOccurrenceRecurrenceRequest(event, request);
        validateSplitStartDate(request, occurrenceDate, event.getRecurrenceEndDate());

        Events modifiedEvent = createModifiedEvent(event, request, occurrenceDate, false, null);
        saveNewOwnerEvent(userEvent, modifiedEvent, label);
        CopiedActionItemResult copiedActionItemResult =
                copyLinkedActionItems(
                        event,
                        modifiedEvent,
                        occurrenceDate,
                        modifiedEvent.getStartDate()
                );

        recurringEventExceptionsRepository.insertDeletedOccurrenceIfAbsent(
                event.getEventId(),
                occurrenceDate,
                RecurringEventExceptionType.DELETED.name()
        );

        eventsRepository.flush();

        return buildUpdateResponse(
                modifiedEvent,
                UpdateScope.SINGLE,
                2,
                copiedActionItemResult.copiedActionItemCount(),
                copiedActionItemResult.requiresActionItemReview(),
                label
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
        validateSplitStartDate(request, occurrenceDate, originalRecurrenceEndDate);
        event.truncateRecurrenceEndDate(occurrenceDate.minusDays(1).atTime(LocalTime.MAX));

        Events modifiedEvent = createModifiedEvent(event, request, occurrenceDate, true, originalRecurrenceEndDate);
        saveNewOwnerEvent(userEvent, modifiedEvent, label);
        CopiedActionItemResult copiedActionItemResult =
                copyLinkedActionItemsFromOccurrence(
                        event,
                        modifiedEvent,
                        occurrenceDate,
                        modifiedEvent.getStartDate()
                );

        eventsRepository.flush();

        return buildUpdateResponse(
                modifiedEvent,
                UpdateScope.THIS_AND_FUTURE,
                2,
                copiedActionItemResult.copiedActionItemCount(),
                copiedActionItemResult.requiresActionItemReview(),
                label
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
        RecurrenceRule recurrenceRule =
                resolveUpdateRecurrenceRule(event, request, startDate, event.getRecurrenceEndDate());

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
        event.updateRecurrenceRule(
                recurrenceRule.isRecurring(),
                recurrenceRule.recurrenceType(),
                recurrenceRule.recurrenceInterval(),
                recurrenceRule.recurrenceDayOfWeek(),
                recurrenceRule.recurrenceDayOfMonth(),
                recurrenceRule.recurrenceEndDate()
        );

        ActionItemAdjustmentResult adjustmentResult =
                adjustLinkedActionItems(event.getEventId(), previousStartDate, startDate);

        eventsRepository.flush();

        return buildUpdateResponse(
                event,
                UpdateScope.THIS_AND_FUTURE,
                1,
                adjustmentResult.adjustedActionItemCount(),
                adjustmentResult.requiresActionItemReview(),
                label
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
        RecurrenceRule recurrenceRule = recurring
                ? resolveUpdateRecurrenceRule(sourceEvent, request, resolvedStartDate, recurrenceEndDate)
                : nonRecurringRule();
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
                recurrenceRule.isRecurring(),
                recurrenceRule.recurrenceType(),
                recurrenceRule.recurrenceInterval(),
                recurrenceRule.recurrenceDayOfWeek(),
                recurrenceRule.recurrenceDayOfMonth(),
                recurrenceRule.recurrenceEndDate(),
                normalizeBlank(request.location()),
                sourceEvent.getEventType(),
                SourceType.USER_MANUAL_EDIT,
                status
        ));
    }

    private void saveNewOwnerEvent(UserEvents sourceUserEvent, Events event, Labels label) {
        userEventsRepository.save(UserEvents.createOwner(sourceUserEvent.getUser(), event, label));
    }

    private CopiedActionItemResult copyLinkedActionItems(
            Events sourceEvent,
            Events targetEvent,
            LocalDate sourceOccurrenceDate,
            LocalDate targetOccurrenceDate
    ) {
        List<ActionItems> sourceActionItems = actionItemsRepository
                .findAllByParentEvent_EventIdAndOccurrenceDateAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        sourceEvent.getEventId(),
                        sourceOccurrenceDate
                );

        if (sourceActionItems.isEmpty()) {
            return new CopiedActionItemResult(0, false);
        }

        List<ActionItems> copiedActionItems = new ArrayList<>();
        boolean requiresActionItemReview = false;

        for (ActionItems sourceActionItem : sourceActionItems) {
            ActionItems copiedActionItem = copyActionItem(
                    sourceActionItem,
                    targetEvent,
                    targetOccurrenceDate,
                    targetOccurrenceDate
            );

            copiedActionItems.add(copiedActionItem);

            if (requiresCopiedActionItemReview(
                    sourceActionItem,
                    copiedActionItem,
                    targetOccurrenceDate
            )) {
                requiresActionItemReview = true;
            }
        }

        actionItemsRepository.saveAll(copiedActionItems);

        // 새 일정에 복사한 뒤 기존 반복 회차의 원본 action-item은
        // F103/F104에서 중복 노출되지 않도록 soft delete
        actionItemsRepository.softDeleteByParentEventIdAndOccurrenceDate(
                sourceEvent.getEventId(),
                sourceOccurrenceDate,
                LocalDateTime.now()
        );

        return new CopiedActionItemResult(
                copiedActionItems.size(),
                requiresActionItemReview
        );
    }

    private CopiedActionItemResult copyLinkedActionItemsFromOccurrence(
            Events sourceEvent,
            Events targetEvent,
            LocalDate sourceOccurrenceDate,
            LocalDate targetSeriesStartDate
    ) {
        List<ActionItems> sourceActionItems = actionItemsRepository
                .findAllByParentEvent_EventIdAndOccurrenceDateGreaterThanEqualAndDeletedAtIsNullOrderByOccurrenceDateAscActionItemIdAsc(
                        sourceEvent.getEventId(),
                        sourceOccurrenceDate
                );

        if (sourceActionItems.isEmpty()) {
            return new CopiedActionItemResult(0, false);
        }

        List<ActionItems> copiedActionItems = new ArrayList<>();
        boolean requiresActionItemReview = false;

        LocalDate currentSourceOccurrence = null;
        LocalDate currentTargetOccurrence = targetSeriesStartDate;

        for (ActionItems sourceActionItem : sourceActionItems) {

            // source occurrence가 다음 회차로 넘어갔다면
            // target 반복 일정에서도 다음 회차를 계산합니다.
            if (!Objects.equals(
                    currentSourceOccurrence,
                    sourceActionItem.getOccurrenceDate()
            )) {
                if (currentSourceOccurrence != null) {
                    currentTargetOccurrence =
                            nextOccurrence(
                                    targetEvent,
                                    currentTargetOccurrence
                            );
                }

                currentSourceOccurrence =
                        sourceActionItem.getOccurrenceDate();
            }

            ActionItems copiedActionItem =
                    copyActionItem(
                            sourceActionItem,
                            targetEvent,
                            currentTargetOccurrence,
                            currentTargetOccurrence
                    );

            copiedActionItems.add(copiedActionItem);

            if (requiresCopiedActionItemReview(
                    sourceActionItem,
                    copiedActionItem,
                    currentTargetOccurrence
            )) {
                requiresActionItemReview = true;
            }
        }

        actionItemsRepository.saveAll(copiedActionItems);

        // 새 반복시리즈로 복사된 기준 회차 이후의 원본 action-item 제거
        actionItemsRepository.softDeleteByParentEventIdFromOccurrenceDate(
                sourceEvent.getEventId(),
                sourceOccurrenceDate,
                LocalDateTime.now()
        );

        return new CopiedActionItemResult(
                copiedActionItems.size(),
                requiresActionItemReview
        );
    }

    private ActionItems copyActionItem(
            ActionItems sourceActionItem,
            Events targetEvent,
            LocalDate targetOccurrenceDate,
            LocalDate targetStartDate
    ){
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
                targetOccurrenceDate,
                displayDate,
                displayDatetime,
                sourceActionItem.getOffsetDays(),
                sourceActionItem.getCreatedBy(),
                sourceActionItem.getSourceTemplateId()
        );
    }

    private boolean requiresCopiedActionItemReview(
            ActionItems sourceActionItem,
            ActionItems copiedActionItem,
            LocalDate targetStartDate
    ) {
        if (sourceActionItem.getItemType() == ItemType.TIMED_ACTION
                && sourceActionItem.getOffsetDays() != null
                && targetStartDate != null) {
            return false;
        }

        LocalDate displayDate = copiedActionItem.getDisplayDate();
        if (displayDate == null) {
            return false;
        }

        return targetStartDate == null || !displayDate.equals(targetStartDate);
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

    private void validateSplitStartDate(
            EventUpdateRequest request,
            LocalDate occurrenceDate,
            LocalDateTime recurrenceEndDate
    ) {
        LocalDate requestedStartDate = parseDate(request.startDate());
        if (requestedStartDate == null) {
            return;
        }

        if (requestedStartDate.isBefore(occurrenceDate)) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        if (recurrenceEndDate != null && requestedStartDate.isAfter(recurrenceEndDate.toLocalDate())) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
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

    private void validateSingleOccurrenceRecurrenceRequest(Events sourceEvent, EventUpdateRequest request) {
        if (!hasRecurrenceRequest(request)) {
            return;
        }

        boolean matchesCurrentRule =
                matchesCurrentIsRecurring(sourceEvent, request.isRecurring())
                        && matchesCurrentRecurrenceType(sourceEvent, request.recurrenceType())
                        && matchesCurrentRecurrenceInterval(sourceEvent, request.recurrenceInterval())
                        && matchesCurrentRecurrenceEndDate(sourceEvent, request.recurrenceEndDate());

        if (!matchesCurrentRule) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private boolean matchesCurrentIsRecurring(Events sourceEvent, Boolean requestedIsRecurring) {
        return requestedIsRecurring == null || Objects.equals(requestedIsRecurring, sourceEvent.getIsRecurring());
    }

    private boolean matchesCurrentRecurrenceType(Events sourceEvent, RecurrenceType requestedType) {
        return requestedType == null || requestedType == sourceEvent.getRecurrenceType();
    }

    private boolean matchesCurrentRecurrenceInterval(Events sourceEvent, Integer requestedInterval) {
        return requestedInterval == null || Objects.equals(requestedInterval, resolveExistingInterval(sourceEvent));
    }

    private boolean matchesCurrentRecurrenceEndDate(Events sourceEvent, String requestedEndDate) {
        if (requestedEndDate == null) {
            return true;
        }

        LocalDateTime parsedEndDate = parseRecurrenceEndDate(requestedEndDate);
        LocalDate currentEndDate = sourceEvent.getRecurrenceEndDate() == null
                ? null
                : sourceEvent.getRecurrenceEndDate().toLocalDate();
        LocalDate requestedDate = parsedEndDate == null
                ? null
                : parsedEndDate.toLocalDate();

        return Objects.equals(requestedDate, currentEndDate);
    }

    private RecurrenceRule resolveUpdateRecurrenceRule(
            Events sourceEvent,
            EventUpdateRequest request,
            LocalDate startDate,
            LocalDateTime fallbackRecurrenceEndDate
    ) {
        if (hasRecurrenceRequest(request)) {
            return resolveRequestedRecurrenceRule(sourceEvent, request, startDate, fallbackRecurrenceEndDate);
        }

        if (!Boolean.TRUE.equals(sourceEvent.getIsRecurring())) {
            return nonRecurringRule();
        }

        LocalDate effectiveStartDate = startDate == null ? sourceEvent.getStartDate() : startDate;
        return preserveExistingRecurrenceRule(sourceEvent, effectiveStartDate, fallbackRecurrenceEndDate);
    }

    private RecurrenceRule resolveRequestedRecurrenceRule(
            Events sourceEvent,
            EventUpdateRequest request,
            LocalDate startDate,
            LocalDateTime fallbackRecurrenceEndDate
    ) {
        RecurrenceType requestedType = request.recurrenceType();
        boolean hasRecurringType = requestedType != null && requestedType != RecurrenceType.NONE;

        if (Boolean.FALSE.equals(request.isRecurring()) && hasRecurringType) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        boolean isRecurring =
                Boolean.TRUE.equals(request.isRecurring())
                        || hasRecurringType
                        || (requestedType == null
                        && Boolean.TRUE.equals(sourceEvent.getIsRecurring())
                        && (request.recurrenceInterval() != null || request.recurrenceEndDate() != null));

        if (!isRecurring || requestedType == RecurrenceType.NONE) {
            return nonRecurringRule();
        }

        RecurrenceType recurrenceType = requestedType == null
                ? sourceEvent.getRecurrenceType()
                : requestedType;
        validateSupportedRecurrenceType(recurrenceType);
        validateRecurringStartDate(startDate);

        int interval = request.recurrenceInterval() == null
                ? resolveExistingInterval(sourceEvent)
                : request.recurrenceInterval();
        validateRecurrenceInterval(interval);

        LocalDateTime recurrenceEndDate = request.recurrenceEndDate() == null
                ? fallbackRecurrenceEndDate
                : parseRecurrenceEndDate(request.recurrenceEndDate());
        validateRecurrenceEndDate(startDate, recurrenceEndDate);

        return buildRecurringRule(recurrenceType, interval, startDate, recurrenceEndDate);
    }

    private RecurrenceRule preserveExistingRecurrenceRule(
            Events sourceEvent,
            LocalDate startDate,
            LocalDateTime recurrenceEndDate
    ) {
        RecurrenceType recurrenceType = sourceEvent.getRecurrenceType();
        validateSupportedRecurrenceType(recurrenceType);
        validateRecurringStartDate(startDate);

        int interval = resolveExistingInterval(sourceEvent);
        validateRecurrenceInterval(interval);
        validateRecurrenceEndDate(startDate, recurrenceEndDate);

        return buildRecurringRule(recurrenceType, interval, startDate, recurrenceEndDate);
    }

    private RecurrenceRule buildRecurringRule(
            RecurrenceType recurrenceType,
            Integer recurrenceInterval,
            LocalDate startDate,
            LocalDateTime recurrenceEndDate
    ) {
        RecurrenceDayOfWeek dayOfWeek = RecurrenceDayOfWeek.NONE;
        Integer dayOfMonth = null;

        if (recurrenceType == RecurrenceType.WEEKLY) {
            dayOfWeek = toRecurrenceDayOfWeek(startDate);
        }

        if (recurrenceType == RecurrenceType.MONTHLY || recurrenceType == RecurrenceType.YEARLY) {
            dayOfMonth = startDate.getDayOfMonth();
        }

        return new RecurrenceRule(
                true,
                recurrenceType,
                recurrenceInterval,
                dayOfWeek,
                dayOfMonth,
                recurrenceEndDate
        );
    }

    private RecurrenceRule nonRecurringRule() {
        return new RecurrenceRule(
                false,
                RecurrenceType.NONE,
                null,
                RecurrenceDayOfWeek.NONE,
                null,
                null
        );
    }

    private boolean hasRecurrenceRequest(EventUpdateRequest request) {
        return request.isRecurring() != null
                || request.recurrenceType() != null
                || request.recurrenceInterval() != null
                || request.recurrenceEndDate() != null;
    }

    private void validateSupportedRecurrenceType(RecurrenceType recurrenceType) {
        if (recurrenceType == null
                || recurrenceType == RecurrenceType.NONE
                || recurrenceType == RecurrenceType.CUSTOM) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private void validateRecurringStartDate(LocalDate startDate) {
        if (startDate == null) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private int resolveExistingInterval(Events sourceEvent) {
        return sourceEvent.getRecurrenceInterval() == null
                ? 1
                : sourceEvent.getRecurrenceInterval();
    }

    private void validateRecurrenceInterval(Integer interval) {
        if (interval == null || interval < 1) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private LocalDateTime parseRecurrenceEndDate(String value) {
        LocalDate recurrenceEndDate = parseDate(value);
        if (recurrenceEndDate == null) {
            return null;
        }
        return recurrenceEndDate.atStartOfDay();
    }

    private void validateRecurrenceEndDate(LocalDate startDate, LocalDateTime recurrenceEndDate) {
        if (recurrenceEndDate != null && recurrenceEndDate.toLocalDate().isBefore(startDate)) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }
    }

    private EventUpdateResponse buildUpdateResponse(
            Events event,
            UpdateScope updateScope,
            Integer affectedEventCount,
            Integer adjustedActionItemCount,
            Boolean requiresActionItemReview,
            Labels label
    ) {
        return new EventUpdateResponse(
                event.getEventId(),
                updateScope,
                event.getEventStatus(),
                affectedEventCount,
                adjustedActionItemCount,
                requiresActionItemReview,
                event.getIsRecurring(),
                event.getRecurrenceType(),
                event.getRecurrenceInterval(),
                event.getRecurrenceDayOfWeek(),
                event.getRecurrenceDayOfMonth(),
                event.getRecurrenceEndDate(),
                resolveLabelId(label),
                event.getUpdatedAt()
        );
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

    private record CopiedActionItemResult(
            Integer copiedActionItemCount,
            Boolean requiresActionItemReview
    ) {
    }

    private record RecurrenceRule(
            Boolean isRecurring,
            RecurrenceType recurrenceType,
            Integer recurrenceInterval,
            RecurrenceDayOfWeek recurrenceDayOfWeek,
            Integer recurrenceDayOfMonth,
            LocalDateTime recurrenceEndDate
    ) {
    }

    /**
     * 반복 일정의 다음 회차 날짜를 계산합니다.
     *
     * 고정 일수 이동이 아니라 recurrenceType과 interval을 기준으로
     * 다음 회차를 계산하여 월/연 반복에서도 순서를 유지합니다.
     */
    private LocalDate nextOccurrence(
            Events event,
            LocalDate currentOccurrence
    ) {
        int interval = event.getRecurrenceInterval() == null
                ? 1
                : event.getRecurrenceInterval();

        return switch (event.getRecurrenceType()) {
            case DAILY ->
                    currentOccurrence.plusDays(interval);

            case WEEKLY ->
                    currentOccurrence.plusWeeks(interval);

            case MONTHLY ->
                    currentOccurrence.plusMonths(interval);

            case YEARLY ->
                    currentOccurrence.plusYears(interval);

            case NONE, CUSTOM ->
                    currentOccurrence;
        };
    }
}
