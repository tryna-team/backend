package com.tryna.domain.event.service;

import com.tryna.domain.action.dto.ActionItemSaveResponse;
import com.tryna.domain.action.service.ActionItemService;
import com.tryna.domain.event.dto.EventCreateRequest;
import com.tryna.domain.event.dto.EventCreateResponse;
import com.tryna.domain.event.dto.EventGraphSyncRequest;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.repository.LabelsRepository;
import com.tryna.domain.user.entity.Users;
import com.tryna.domain.user.repository.UserRepository;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.EventErrorCode;
import com.tryna.global.exception.UserErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventCommandService {

    private static final int MAX_TITLE_LENGTH = 255;

    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;
    private final UserRepository userRepository;
    private final ActionItemService actionItemService;
    private final LabelsRepository labelsRepository;
    private final BrainServiceClient brainServiceClient;

    @Transactional
    public EventCreateResponse createEvent(Long userId, EventCreateRequest request) {
        if (request == null) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_404));

        validateRequiredText(request.eventTitle());
        validateTitleLength(request.eventTitle());

        LocalDate startDate = parseDate(request.startDate());
        LocalTime startTime = parseTime(request.startTime());
        LocalDate endDate = parseDate(request.endDate());
        LocalTime endTime = parseTime(request.endTime());
        boolean isAllDay = resolveAllDay(request.isAllDay(), startTime);
        validateTimePolicy(startDate, startTime, endDate, endTime, isAllDay);
        RecurrenceRule recurrenceRule = resolveRecurrenceRule(request, startDate);
        Labels label = resolveEventLabel(userId, request.labelId());

        EventStatus status = startDate == null ? EventStatus.NEEDS_CONFIRMATION : EventStatus.CONFIRMED;
        Events event = Events.createInternalEvent(
                request.eventTitle().trim(),
                request.eventTitle().trim(),
                normalizeBlank(request.description()),
                startDate,
                combine(startDate, startTime),
                endDate,
                combine(endDate, endTime),
                isAllDay,
                recurrenceRule.isRecurring(),
                recurrenceRule.recurrenceType(),
                recurrenceRule.recurrenceInterval(),
                recurrenceRule.recurrenceDayOfWeek(),
                recurrenceRule.recurrenceDayOfMonth(),
                recurrenceRule.recurrenceEndDate(),
                normalizeBlank(request.location()),
                normalizeBlank(request.eventType()),
                status
        );

        Events savedEvent = eventsRepository.save(event);
        userEventsRepository.save(UserEvents.createOwner(user, savedEvent, label));
        ActionItemSaveResponse savedActionItems =
                actionItemService.saveActionItemsForEvent(
                        user,
                        savedEvent,
                        request.actionItems()
                );

        // DB 트랜잭션 커밋 완료 후 Python 서버로 비동기 그래프 동기화 요청 전송
        try {
            List<EventGraphSyncRequest.EntityDto> graphEntities = request.entities() != null
                    ? request.entities().stream()
                    .map(e -> new EventGraphSyncRequest.EntityDto(e.name(), e.role()))
                    .toList()
                    : List.of();

            EventGraphSyncRequest syncRequest = new EventGraphSyncRequest(
                    savedEvent.getEventId(),
                    userId, // ownerId
                    1,      // eventVersion (최초 생성 초기값)
                    savedEvent.getTitle(),
                    request.activityCode(),
                    graphEntities
            );

            Runnable syncTask = () -> {
                try {
                    brainServiceClient.requestGraphSync(syncRequest);
                } catch (Exception e) {
                    log.error("Python 서버로의 그래프 동기화 요청 실패: eventId={}", savedEvent.getEventId(), e);
                }
            };

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        syncTask.run();
                    }
                });
            } else {
                syncTask.run();
            }
        } catch (Exception e) {
            log.error("Python 서버로의 그래프 동기화 예약 실패: eventId={}", savedEvent.getEventId(), e);
        }

        return new EventCreateResponse(
                savedEvent.getEventId(),
                savedEvent.getEventStatus(),
                savedEvent.getSourceType(),
                savedEvent.getIsRecurring(),
                savedEvent.getRecurrenceType(),
                savedEvent.getRecurrenceInterval(),
                savedEvent.getRecurrenceDayOfWeek(),
                savedEvent.getRecurrenceDayOfMonth(),
                savedEvent.getRecurrenceEndDate(),
                label.getLabelId(),
                savedEvent.getCreatedAt(),
                savedActionItems.items()
        );
    }

    private void validateRequiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }
    }

    private void validateTitleLength(String title) {
        if (title.trim().length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
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
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        if (startDate == null && startTime != null) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        if (startDate == null && endDate != null) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        if (endTime != null && endDate == null) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }
    }

    private RecurrenceRule resolveRecurrenceRule(EventCreateRequest request, LocalDate startDate) {
        RecurrenceType requestedType = request.recurrenceType();
        boolean hasRecurrenceType = requestedType != null && requestedType != RecurrenceType.NONE;

        if (Boolean.FALSE.equals(request.isRecurring()) && hasRecurrenceType) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        boolean isRecurring = Boolean.TRUE.equals(request.isRecurring()) || hasRecurrenceType;

        if (!isRecurring) {
            return new RecurrenceRule(
                    false,
                    RecurrenceType.NONE,
                    null,
                    RecurrenceDayOfWeek.NONE,
                    null,
                    null
            );
        }

        if (startDate == null || requestedType == null || requestedType == RecurrenceType.NONE) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        if (requestedType != RecurrenceType.DAILY
                && requestedType != RecurrenceType.WEEKLY
                && requestedType != RecurrenceType.MONTHLY
                && requestedType != RecurrenceType.YEARLY) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        int interval = request.recurrenceInterval() == null ? 1 : request.recurrenceInterval();
        if (interval < 1) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        LocalDateTime recurrenceEndDate = parseRecurrenceEndDate(request.recurrenceEndDate());
        if (recurrenceEndDate != null && recurrenceEndDate.toLocalDate().isBefore(startDate)) {
            throw new BusinessException(EventErrorCode.C104_EVENT_SAVE_400);
        }

        RecurrenceDayOfWeek dayOfWeek = RecurrenceDayOfWeek.NONE;
        Integer dayOfMonth = null;

        if (requestedType == RecurrenceType.WEEKLY) {
            dayOfWeek = toRecurrenceDayOfWeek(startDate.getDayOfWeek());
        }

        if (requestedType == RecurrenceType.MONTHLY || requestedType == RecurrenceType.YEARLY) {
            dayOfMonth = startDate.getDayOfMonth();
        }

        return new RecurrenceRule(
                true,
                requestedType,
                interval,
                dayOfWeek,
                dayOfMonth,
                recurrenceEndDate
        );
    }

    private LocalDateTime parseRecurrenceEndDate(String value) {
        LocalDate recurrenceEndDate = parseDate(value);
        if (recurrenceEndDate == null) {
            return null;
        }
        return recurrenceEndDate.atStartOfDay();
    }

    private RecurrenceDayOfWeek toRecurrenceDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> RecurrenceDayOfWeek.MON;
            case TUESDAY -> RecurrenceDayOfWeek.TUE;
            case WEDNESDAY -> RecurrenceDayOfWeek.WED;
            case THURSDAY -> RecurrenceDayOfWeek.THU;
            case FRIDAY -> RecurrenceDayOfWeek.FRI;
            case SATURDAY -> RecurrenceDayOfWeek.SAT;
            case SUNDAY -> RecurrenceDayOfWeek.SUN;
        };
    }

    private Labels resolveEventLabel(Long userId, Long requestedLabelId) {
        if (requestedLabelId != null) {
            return labelsRepository.findByLabelIdAndUser_UserId(requestedLabelId, userId)
                    .orElseThrow(() -> new BusinessException(EventErrorCode.C104_EVENT_SAVE_400));
        }

        return labelsRepository.findByUser_UserIdAndIsDefaultTrue(userId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.C104_EVENT_SAVE_400));
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

    private record RecurrenceRule(
            Boolean isRecurring,
            RecurrenceType recurrenceType,
            Integer recurrenceInterval,
            RecurrenceDayOfWeek recurrenceDayOfWeek,
            Integer recurrenceDayOfMonth,
            LocalDateTime recurrenceEndDate
    ) {
    }
}
