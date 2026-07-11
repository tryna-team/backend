package com.tryna.domain.event.service;

import com.tryna.domain.event.dto.CalendarDateEventsResponse;
import com.tryna.domain.event.dto.CalendarMonthlyResponse;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventErrorCode;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
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
    private static final String NO_SELECTED_DATE_EVENTS = "NO_SELECTED_DATE_EVENTS";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final EnumSet<EventStatus> VISIBLE_EVENT_STATUSES = EnumSet.of(
            EventStatus.CONFIRMED,
            EventStatus.NEEDS_CONFIRMATION
    );

    private final UserEventsRepository userEventsRepository;

    public CalendarMonthlyResponse getMonthlyCalendar(Long userId, Integer year, Integer month) {
        validateUserId(userId);
        validateYearMonth(year, month);

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
        LocalDate date = parseDate(dateValue);
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

    private void validateYearMonth(Integer year, Integer month) {
        if (year == null || year < MIN_YEAR || year > MAX_YEAR || month == null || month < 1 || month > 12) {
            throw new BusinessException(EventErrorCode.B102_CALENDAR_MONTHLY_400);
        }
    }

    private LocalDate parseDate(String dateValue) {
        try {
            return LocalDate.parse(dateValue);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessException(EventErrorCode.B013_CALENDAR_DATE_EVENTS_400);
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

    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        LocalTime time = dateTime.toLocalTime();
        return time.format(TIME_FORMATTER);
    }
}
