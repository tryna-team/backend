package com.tryna.domain.event.service;

import com.tryna.domain.event.dto.CalendarDateEventsResponse;
import com.tryna.domain.event.dto.CalendarMainResponse;
import com.tryna.domain.event.dto.CalendarMonthlyResponse;
import com.tryna.domain.event.dto.EventDetailResponse;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.EventErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MIN_YEAR = 1970;
    private static final int MAX_YEAR = 2100;
    private static final String NO_EVENTS = "NO_EVENTS";
    private static final String NO_SELECTED_DATE_EVENTS = "NO_SELECTED_DATE_EVENTS";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final EnumSet<EventStatus> VISIBLE_EVENT_STATUSES = EnumSet.of(
            EventStatus.CONFIRMED,
            EventStatus.NEEDS_CONFIRMATION
    );

    private final EventsRepository eventsRepository;
    private final UserEventsRepository userEventsRepository;

    public CalendarMainResponse getCalendarMain(Long userId, Integer year, Integer month, String selectedDateValue) {
        validateUserId(userId);
        validateYearMonth(year, month, EventErrorCode.B101_CALENDAR_MAIN_400);
        LocalDate selectedDate = parseDate(selectedDateValue, EventErrorCode.B101_CALENDAR_MAIN_400);
        validateSelectedDateInMonth(year, month, selectedDate);

        CalendarMonthlyResponse monthlyCalendar = buildMonthlyCalendar(userId, year, month);
        CalendarDateEventsResponse dateEvents = buildDateEvents(userId, selectedDate);
        boolean hasEvents = userEventsRepository.countVisibleEventsByUserId(userId, VISIBLE_EVENT_STATUSES) > 0;

        List<CalendarMonthlyResponse.DayEventCount> monthlyEventDays = monthlyCalendar.days()
                .stream()
                .filter(CalendarMonthlyResponse.DayEventCount::hasEvent)
                .toList();

        return new CalendarMainResponse(
                year,
                month,
                monthlyCalendar.today(),
                selectedDate,
                hasEvents,
                false,
                resolveMainEmptyState(hasEvents, dateEvents),
                monthlyEventDays,
                dateEvents.events()
        );
    }

    public CalendarMonthlyResponse getMonthlyCalendar(Long userId, Integer year, Integer month) {
        validateUserId(userId);
        validateYearMonth(year, month, EventErrorCode.B102_CALENDAR_MONTHLY_400);

        return buildMonthlyCalendar(userId, year, month);
    }

    private CalendarMonthlyResponse buildMonthlyCalendar(Long userId, Integer year, Integer month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        Map<LocalDate, Long> countsByDate = getCountsByDate(userId, startDate, endDate);

        List<CalendarMonthlyResponse.DayEventCount> days = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Long eventCount = countsByDate.getOrDefault(date, 0L);
            days.add(new CalendarMonthlyResponse.DayEventCount(
                    date,
                    eventCount,
                    eventCount > 0
            ));
        }

        return new CalendarMonthlyResponse(
                year,
                month,
                LocalDate.now(SERVICE_ZONE),
                days
        );
    }

    public CalendarDateEventsResponse getDateEvents(Long userId, String dateValue) {
        validateUserId(userId);
        LocalDate date = parseDate(dateValue, EventErrorCode.B103_CALENDAR_DATE_EVENTS_400);

        return buildDateEvents(userId, date);
    }

    private String resolveMainEmptyState(boolean hasEvents, CalendarDateEventsResponse dateEvents) {
        if (!hasEvents) {
            return NO_EVENTS;
        }
        return dateEvents.emptyStateType();
    }

    private CalendarDateEventsResponse buildDateEvents(Long userId, LocalDate date) {
        List<CalendarDateEventsResponse.EventSummary> events = userEventsRepository.findEventsByDate(
                        userId,
                        date,
                        VISIBLE_EVENT_STATUSES
                )
                .stream()
                .map(this::toEventSummary)
                .toList();

        return new CalendarDateEventsResponse(
                date,
                events.size(),
                events.isEmpty() ? NO_SELECTED_DATE_EVENTS : null,
                events
        );
    }

    public EventDetailResponse getEventDetail(Long userId, String eventIdValue) {
        validateUserId(userId);
        Long eventId = parseEventId(eventIdValue);

        if (!eventsRepository.existsVisibleByEventIdAndEventStatusIn(eventId, VISIBLE_EVENT_STATUSES)) {
            throw new BusinessException(EventErrorCode.B104_EVENT_DETAIL_404);
        }

        Events event = eventsRepository.findVisibleEventAccessibleToUser(
                        userId,
                        eventId,
                        VISIBLE_EVENT_STATUSES,
                        ConnectionStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(EventErrorCode.B104_EVENT_DETAIL_403));

        return toEventDetailResponse(event);
    }

    private Map<LocalDate, Long> getCountsByDate(Long userId, LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = userEventsRepository.countEventsByDate(
                userId,
                startDate,
                endDate,
                VISIBLE_EVENT_STATUSES
        );

        Map<LocalDate, Long> countsByDate = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            Long count = (Long) row[1];
            countsByDate.put(date, count);
        }
        return countsByDate;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(AuthErrorCode.AUTH_401);
        }
    }

    private void validateYearMonth(Integer year, Integer month, EventErrorCode errorCode) {
        if (year == null || year < MIN_YEAR || year > MAX_YEAR || month == null || month < 1 || month > 12) {
            throw new BusinessException(errorCode);
        }
    }

    private void validateSelectedDateInMonth(Integer year, Integer month, LocalDate selectedDate) {
        if (selectedDate.getYear() != year || selectedDate.getMonthValue() != month) {
            throw new BusinessException(EventErrorCode.B101_CALENDAR_MAIN_400);
        }
    }

    private LocalDate parseDate(String dateValue, EventErrorCode errorCode) {
        try {
            return LocalDate.parse(dateValue);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException(errorCode);
        }
    }

    private Long parseEventId(String eventIdValue) {
        try {
            Long eventId = Long.parseLong(eventIdValue);
            if (eventId <= 0) {
                throw new NumberFormatException();
            }
            return eventId;
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(EventErrorCode.B104_EVENT_DETAIL_400);
        }
    }

    private CalendarDateEventsResponse.EventSummary toEventSummary(Events event) {
        return new CalendarDateEventsResponse.EventSummary(
                event.getEventId(),
                event.getTitle(),
                event.getStartDate(),
                formatTime(event.getStartDatetime()),
                event.getEndDate(),
                formatTime(event.getEndDatetime()),
                event.getIsAllDay(),
                event.getLocation(),
                event.getSourceType(),
                event.getEventStatus()
        );
    }

    private EventDetailResponse toEventDetailResponse(Events event) {
        return new EventDetailResponse(
                event.getEventId(),
                event.getSourceText(),
                event.getTitle(),
                event.getDescription(),
                event.getStartDate(),
                formatTime(event.getStartDatetime()),
                event.getEndDate(),
                formatTime(event.getEndDatetime()),
                event.getIsAllDay(),
                event.getLocation(),
                event.getEventTypeCandidate(),
                event.getEventType(),
                event.getSourceType(),
                event.getEventStatus(),
                event.getExternalEventId(),
                event.getProvider()
        );
    }

    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        LocalTime time = dateTime.toLocalTime();
        return time.format(TIME_FORMATTER);
    }
}
