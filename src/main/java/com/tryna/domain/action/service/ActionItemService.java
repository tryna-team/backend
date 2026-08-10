package com.tryna.domain.action.service;

import com.tryna.domain.action.dto.ActionItemSaveRequest;
import com.tryna.domain.action.dto.ActionItemSaveResponse;
import com.tryna.domain.action.dto.ActionItemStatusUpdateRequest;
import com.tryna.domain.action.dto.ActionItemStatusUpdateResponse;
import com.tryna.domain.action.dto.EventActionItemResponse;
import com.tryna.domain.action.dto.TimedActionItemResponse;
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
import com.tryna.domain.recommendation.entity.mapping.RecommendationFeedbacks;
import com.tryna.domain.recommendation.repository.RecommendationFeedbacksRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.ActionErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActionItemService {

    private final ActionItemsRepository actionItemsRepository;
    private final ActionItemOccurrenceStatesRepository actionItemOccurrenceStatesRepository;
    private final RecommendationFeedbacksRepository recommendationFeedbacksRepository;
    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final UserRepository userRepository;

    @Transactional
    public ActionItemSaveResponse saveActionItems(
            Long userId,
            Long eventId,
            ActionItemSaveRequest request
    ) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ActionErrorCode.E105_ACTION_ITEM_400_1);
        }

        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_403));

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_403));

        if (!userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)) {
            throw new BusinessException(CommonErrorCode.COMMON_403);
        }

        return saveActionItemsForEvent(user, event, request);
    }

    @Transactional
    public ActionItemSaveResponse saveActionItemsForEvent(
            Users user,
            Events event,
            ActionItemSaveRequest request
    ) {
        List<ActionItems> savedActionItems = List.of();

        if (request != null && request.items() != null && !request.items().isEmpty()) {
            validateActionItems(request.items(), event);

            List<ActionItems> actionItems = request.items().stream()
                    .map(item -> ActionItems.create(
                            event,
                            item.title(),
                            item.itemType(),
                            resolveOccurrenceDate(event, item.occurrenceDate()),
                            item.displayDate(),
                            item.displayTime(),
                            item.offsetDays(),
                            item.createdBy(),
                            item.sourceTemplateId()
                    ))
                    .toList();

            savedActionItems = actionItemsRepository.saveAll(actionItems);
        }

        List<RecommendationFeedbacks> feedbacks = safeFeedbackLogs(request).stream()
                .map(feedback -> RecommendationFeedbacks.create(
                        user,
                        event,
                        feedback.sourceTemplateId(),
                        feedback.actionType(),
                        feedback.originalTitle(),
                        feedback.editedTitle(),
                        feedback.reason()
                ))
                .toList();

        if (!feedbacks.isEmpty()) {
            recommendationFeedbacksRepository.saveAll(feedbacks);
        }

        return ActionItemSaveResponse.from(event.getEventId(), savedActionItems);
    }

    private List<ActionItemSaveRequest.Feedback> safeFeedbackLogs(ActionItemSaveRequest request) {
        if (request == null || request.feedbackLogs() == null) {
            return List.of();
        }
        return request.feedbackLogs();
    }

    private void validateActionItems(
            List<ActionItemSaveRequest.Item> items,
            Events event
    ) {
        boolean hasInvalidItem = items.stream()
                .anyMatch(item -> {
                    LocalDate occurrenceDate = resolveOccurrenceDate(event, item.occurrenceDate());

                    if (!isValidOccurrenceDate(event, occurrenceDate)) {
                        return true;
                    }

                    return hasInvalidDisplayFields(item);
                });

        if (hasInvalidItem) {
            throw new BusinessException(ActionErrorCode.E105_ACTION_ITEM_400_2);
        }
    }

    private boolean hasInvalidDisplayFields(ActionItemSaveRequest.Item item) {
        return switch (item.itemType()) {
            case TIMED_ACTION -> item.displayDate() == null;
            case UNTIMED_PREP -> item.displayDate() != null || item.displayTime() != null;
            case UNRESOLVED -> false;
        };
    }

    private LocalDate resolveOccurrenceDate(
            Events event,
            LocalDate requestedOccurrenceDate
    ) {
        if (requestedOccurrenceDate != null) {
            return requestedOccurrenceDate;
        }

        return event.getStartDate();
    }

    private boolean isValidOccurrenceDate(
            Events event,
            LocalDate occurrenceDate
    ) {
        if (event.getStartDate() == null || occurrenceDate == null) {
            return false;
        }

        if (!Boolean.TRUE.equals(event.getIsRecurring())) {
            return event.getStartDate().equals(occurrenceDate);
        }

        return isRecurringOccurrenceOn(event, occurrenceDate);
    }

    private boolean isRecurringOccurrenceOn(
            Events event,
            LocalDate date
    ) {
        if (!Boolean.TRUE.equals(event.getIsRecurring())
                || event.getStartDate() == null
                || date == null
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

    private boolean isWeeklyOccurrence(
            Events event,
            LocalDate date,
            int interval
    ) {
        RecurrenceDayOfWeek expectedDayOfWeek = event.getRecurrenceDayOfWeek();

        if (expectedDayOfWeek == null || expectedDayOfWeek == RecurrenceDayOfWeek.NONE) {
            expectedDayOfWeek = toRecurrenceDayOfWeek(event.getStartDate());
        }

        return expectedDayOfWeek == toRecurrenceDayOfWeek(date)
                && ChronoUnit.WEEKS.between(event.getStartDate(), date) % interval == 0;
    }

    private boolean isMonthlyOccurrence(
            Events event,
            LocalDate date,
            int interval
    ) {
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

    private boolean isYearlyOccurrence(
            Events event,
            LocalDate date,
            int interval
    ) {
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

    @Transactional
    public ActionItemStatusUpdateResponse updateActionItemStatus(
            Long userId,
            Long actionItemId,
            ActionItemStatusUpdateRequest request
    ) {
        ActionItemStatus requestedStatus = request.actionItemStatus();

        if (requestedStatus != ActionItemStatus.COMPLETED
                && requestedStatus != ActionItemStatus.PENDING) {
            throw new BusinessException(ActionErrorCode.E106_ACTION_ITEM_400);
        }

        ActionItems actionItem = actionItemsRepository
                .findByActionItemIdAndDeletedAtIsNull(actionItemId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.E106_ACTION_ITEM_404));

        Long eventId = actionItem.getParentEvent().getEventId();
        if (!userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)) {
            throw new BusinessException(CommonErrorCode.COMMON_403);
        }

        LocalDate occurrenceDate = resolveOccurrenceDate(
                actionItem.getParentEvent(),
                request.occurrenceDate() == null ? actionItem.getOccurrenceDate() : request.occurrenceDate()
        );

        if (!isValidOccurrenceDate(actionItem.getParentEvent(), occurrenceDate)) {
            throw new BusinessException(ActionErrorCode.E106_ACTION_ITEM_400);
        }

        if (Boolean.TRUE.equals(actionItem.getParentEvent().getIsRecurring())) {
            ActionItemOccurrenceStates occurrenceState = actionItemOccurrenceStatesRepository
                    .findByActionItem_ActionItemIdAndOccurrenceDate(actionItemId, occurrenceDate)
                    .orElseGet(() -> ActionItemOccurrenceStates.create(actionItem, occurrenceDate));

            occurrenceState.updateStatus(requestedStatus);
            actionItemOccurrenceStatesRepository.save(occurrenceState);

            return ActionItemStatusUpdateResponse.from(
                    actionItem,
                    occurrenceDate,
                    occurrenceState.getActionItemStatus(),
                    occurrenceState.getCompletedAt()
            );
        }

        actionItem.updateStatus(requestedStatus);
        return ActionItemStatusUpdateResponse.from(actionItem, occurrenceDate, actionItem.getActionItemStatus(), actionItem.getCompletedAt());
    }

    public EventActionItemResponse getEventActionItems(
            Long userId,
            Long eventId,
            String occurrenceDateText
    ) {
        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.F103_ACTION_ITEM_404));

        if (!userEventsRepository.existsByUser_UserIdAndEvent_EventId(userId, eventId)) {
            throw new BusinessException(CommonErrorCode.COMMON_403);
        }

        LocalDate occurrenceDate = parseOccurrenceDate(occurrenceDateText, ActionErrorCode.F103_ACTION_ITEM_400);

        if (Boolean.TRUE.equals(event.getIsRecurring())) {
            if (!isValidOccurrenceDate(event, occurrenceDate)) {
                throw new BusinessException(ActionErrorCode.F103_ACTION_ITEM_400);
            }

            List<ActionItems> actionItems = actionItemsRepository
                    .findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(eventId);

            return EventActionItemResponse.fromOccurrence(
                    eventId,
                    actionItems,
                    occurrenceDate,
                    findStatesByActionItemId(actionItems, occurrenceDate)
            );
        }

        List<ActionItems> actionItems = actionItemsRepository
                .findAllByParentEvent_EventIdAndOccurrenceDateAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
                        eventId,
                        occurrenceDate
                );

        return EventActionItemResponse.from(eventId, actionItems);
    }

    public TimedActionItemResponse getTimedActionItems(
            Long userId,
            String dateText
    ) {
        LocalDate date = parseOccurrenceDate(dateText, ActionErrorCode.F104_ACTION_ITEM_400);

        List<TimedActionItemResponse.Item> items = new ArrayList<>();

        List<ActionItems> directActionItems = actionItemsRepository
                .findCalendarActionItemsByDate(userId, date, ItemType.TIMED_ACTION);

        items.addAll(directActionItems.stream()
                .map(TimedActionItemResponse.Item::from)
                .toList());

        List<ActionItems> recurringCandidates = actionItemsRepository
                .findRecurringTimedActionItemsByUserId(userId, ItemType.TIMED_ACTION);

        List<RecurringActionItemOccurrence> recurringOccurrences = recurringCandidates.stream()
                .map(actionItem -> toRecurringActionItemOccurrence(actionItem, date))
                .filter(Objects::nonNull)
                .toList();

        Map<StateKey, ActionItemOccurrenceStates> statesByKey = findStatesByKey(recurringOccurrences);

        items.addAll(recurringOccurrences.stream()
                .map(occurrence -> TimedActionItemResponse.Item.fromOccurrence(
                        occurrence.actionItem(),
                        occurrence.occurrenceDate(),
                        date,
                        occurrence.displayTime(),
                        statesByKey.get(new StateKey(
                                occurrence.actionItem().getActionItemId(),
                                occurrence.occurrenceDate()
                        ))
                ))
                .toList());

        List<TimedActionItemResponse.Item> sortedItems = items.stream()
                .sorted(Comparator
                        .comparing(TimedActionItemResponse.Item::displayTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TimedActionItemResponse.Item::actionItemId))
                .toList();

        return TimedActionItemResponse.of(date, sortedItems);
    }

    private LocalDate parseOccurrenceDate(String dateText, ActionErrorCode errorCode) {
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException(errorCode);
        }
    }

    private Map<Long, ActionItemOccurrenceStates> findStatesByActionItemId(
            List<ActionItems> actionItems,
            LocalDate occurrenceDate
    ) {
        List<Long> actionItemIds = actionItems.stream()
                .map(ActionItems::getActionItemId)
                .toList();

        if (actionItemIds.isEmpty()) {
            return Map.of();
        }

        return actionItemOccurrenceStatesRepository
                .findByActionItem_ActionItemIdInAndOccurrenceDate(actionItemIds, occurrenceDate)
                .stream()
                .collect(Collectors.toMap(
                        state -> state.getActionItem().getActionItemId(),
                        Function.identity()
                ));
    }

    private Map<StateKey, ActionItemOccurrenceStates> findStatesByKey(
            List<RecurringActionItemOccurrence> occurrences
    ) {
        List<Long> actionItemIds = occurrences.stream()
                .map(occurrence -> occurrence.actionItem().getActionItemId())
                .distinct()
                .toList();

        List<LocalDate> occurrenceDates = occurrences.stream()
                .map(RecurringActionItemOccurrence::occurrenceDate)
                .distinct()
                .toList();

        if (actionItemIds.isEmpty() || occurrenceDates.isEmpty()) {
            return Map.of();
        }

        return actionItemOccurrenceStatesRepository
                .findByActionItem_ActionItemIdInAndOccurrenceDateIn(actionItemIds, occurrenceDates)
                .stream()
                .collect(Collectors.toMap(
                        state -> new StateKey(
                                state.getActionItem().getActionItemId(),
                                state.getOccurrenceDate()
                        ),
                        Function.identity()
                ));
    }

    private RecurringActionItemOccurrence toRecurringActionItemOccurrence(
            ActionItems actionItem,
            LocalDate displayDate
    ) {
        if (actionItem.getOffsetDays() == null) {
            return null;
        }

        LocalDate occurrenceDate = displayDate.minusDays(actionItem.getOffsetDays());
        if (!isValidOccurrenceDate(actionItem.getParentEvent(), occurrenceDate)) {
            return null;
        }

        return new RecurringActionItemOccurrence(
                actionItem,
                occurrenceDate,
                resolveDisplayTime(actionItem, displayDate)
        );
    }

    private LocalDateTime resolveDisplayTime(ActionItems actionItem, LocalDate displayDate) {
        if (actionItem.getDisplayDatetime() == null || displayDate == null) {
            return actionItem.getDisplayDatetime();
        }

        return LocalDateTime.of(displayDate, actionItem.getDisplayDatetime().toLocalTime());
    }

    private record RecurringActionItemOccurrence(
            ActionItems actionItem,
            LocalDate occurrenceDate,
            LocalDateTime displayTime
    ) {
    }

    private record StateKey(
            Long actionItemId,
            LocalDate occurrenceDate
    ) {
    }
}
