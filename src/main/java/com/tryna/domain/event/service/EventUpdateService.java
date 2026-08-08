package com.tryna.domain.event.service;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.dto.EventUpdateRequest;
import com.tryna.domain.event.dto.EventUpdateResponse;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.event.enums.UpdateScope;
import com.tryna.domain.event.repository.EventsRepository;
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
        Labels label = findOwnedLabel(userId, request.labelId());

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
                label.getLabelId(),
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

        if (request.labelId() == null || request.updateScope() != UpdateScope.SINGLE) {
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

        if (Boolean.TRUE.equals(event.getIsRecurring())) {
            throw new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400);
        }

        if (!userEventsRepository.existsOwnerByUserIdAndEventId(userId, event.getEventId())) {
            throw new BusinessException(CommonErrorCode.COMMON_403);
        }
    }

    private Labels findOwnedLabel(Long userId, Long labelId) {
        return labelsRepository.findByLabelIdAndUser_UserId(labelId, userId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.C107_EVENT_UPDATE_400));
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
