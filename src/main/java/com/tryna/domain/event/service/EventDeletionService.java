package com.tryna.domain.event.service;

import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.dto.EventDeleteRequest;
import com.tryna.domain.event.dto.EventDeleteResponse;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.DeleteScope;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.domain.reminder.service.ReminderLifecycleService;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
import com.tryna.global.exception.EventErrorCode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventDeletionService {

    private static final EnumSet<EventStatus> DELETABLE_EVENT_STATUSES = EnumSet.of(
            EventStatus.CONFIRMED,
            EventStatus.NEEDS_CONFIRMATION
    );

    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final ActionItemsRepository actionItemsRepository;
    private final ReminderLifecycleService reminderLifecycleService;

    @Transactional
    public boolean softDeleteEvent(Long eventId) {
        int updated = eventsRepository.softDeleteById(eventId, LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }

        reminderLifecycleService.cancelScheduledForSoftDeletedEvent(eventId);
        return true;
    }

    @Transactional
    public EventDeleteResponse deleteEvent(Long userId, Long eventId, EventDeleteRequest request) {
        validateDeleteRequest(request);

        if (!eventsRepository.existsVisibleByEventIdAndEventStatusIn(eventId, DELETABLE_EVENT_STATUSES)) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_404);
        }

        Events event = eventsRepository.findVisibleEventAccessibleToUser(
                        userId,
                        eventId,
                        DELETABLE_EVENT_STATUSES,
                        ConnectionStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_403));

        validateDeletableEvent(userId, event, request);

        LocalDateTime deletedAt = LocalDateTime.now();
        reminderLifecycleService.cancelScheduledForSoftDeletedActionItemsByParentEvent(eventId);
        int affectedActionItemCount = actionItemsRepository.softDeleteByParentEventId(eventId, deletedAt);
        int affectedEventCount = eventsRepository.softDeleteById(eventId, deletedAt);

        if (affectedEventCount <= 0) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_404);
        }

        reminderLifecycleService.cancelScheduledForSoftDeletedEvent(eventId);

        return new EventDeleteResponse(
                eventId,
                request.deleteScope(),
                EventStatus.DELETED,
                affectedEventCount,
                affectedActionItemCount
        );
    }

    private void validateDeleteRequest(EventDeleteRequest request) {
        if (request == null
                || request.deleteScope() == null
                || !Boolean.TRUE.equals(request.cascade())) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_400);
        }
    }

    private void validateDeletableEvent(Long userId, Events event, EventDeleteRequest request) {
        if (event.getSourceType() == SourceType.EXTERNAL_CALENDAR) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_400);
        }

        if (!userEventsRepository.existsOwnerByUserIdAndEventId(userId, event.getEventId())) {
            throw new BusinessException(CommonErrorCode.COMMON_403);
        }

        if (request.deleteScope() == DeleteScope.THIS_AND_FUTURE) {
            throw new BusinessException(EventErrorCode.C106_EVENT_DELETE_400);
        }
    }
}
