package com.tryna.domain.event.service;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.dto.*;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

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
    private final ActionItemsRepository actionItemsRepository;

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

    /**
     * B107: 키워드 검색
     *
     * 현재 사용자의 Tryna 내부 일정 제목과 저장된 준비/실행 항목 제목에서
     * 검색어가 포함된 결과를 조회합니다.
     *
     * 일정 제목이 직접 매칭된 결과를 우선 배치하고,
     * 동일 일정의 준비/실행 항목도 매칭된 경우 일정 바로 다음에 배치합니다.
     *
     * 준비/실행 항목만 매칭된 경우 부모 일정은 별도의 EVENT 결과로 반환하지 않고,
     * ACTION_ITEM 결과에 부모 일정 정보를 포함합니다.
     *
     * @param userId 현재 인증된 사용자 ID
     * @param keywordValue 검색 키워드
     * @return 일정 및 준비/실행 항목 키워드 검색 결과
     */
    public EventSearchResponse searchEvents(
            Long userId,
            String keywordValue
    ) {
        // 1. 인증 사용자 ID 검증
        validateUserId(userId);

        // 2. 검색어 앞뒤 공백 제거 및 유효성 검증
        String keyword = normalizeSearchKeyword(keywordValue);

        // 3. 일정 제목이 검색어와 일치한 Tryna 내부 일정 조회
        List<Events> matchedEvents = userEventsRepository
                .findInternalEventsByTitleContaining(
                        userId,
                        keyword,
                        VISIBLE_EVENT_STATUSES,
                        SourceType.EXTERNAL_CALENDAR
                );

        // 4. 준비/실행 항목 제목이 검색어와 일치한 저장 항목 조회
        List<ActionItems> matchedActionItems = actionItemsRepository
                .findSearchMatchesByUserIdAndKeyword(
                        userId,
                        keyword,
                        VISIBLE_EVENT_STATUSES,
                        SourceType.EXTERNAL_CALENDAR
                );

        // 5. 일정 제목이 직접 매칭된 일정 ID 구성
        Set<Long> titleMatchedEventIds = matchedEvents.stream()
                .map(Events::getEventId)
                .collect(java.util.stream.Collectors.toSet());

        // 6. 매칭된 준비/실행 항목을 부모 일정 ID 기준으로 그룹화
        Map<Long, List<ActionItems>> actionItemsByEventId =
                matchedActionItems.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                actionItem -> actionItem.getParentEvent().getEventId(),
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()
                        ));

        // 7. 부모 일정 ID를 기준으로 준비/실행 항목 순서 고정
        actionItemsByEventId.values().forEach(actionItems ->
                actionItems.sort(
                        Comparator.comparing(ActionItems::getActionItemId)
                )
        );

        LocalDate today = LocalDate.now(SERVICE_ZONE);
        Comparator<Events> eventComparator = eventSearchComparator(today);

        List<EventSearchResponse.Result> results = new ArrayList<>();

        // 8. 일정 제목이 직접 매칭된 결과를 날짜가 가까운 순으로 우선 배치
        matchedEvents.stream()
                .sorted(eventComparator)
                .forEach(event -> {
                    // 8-1. EVENT 결과 추가
                    results.add(EventSearchResponse.Result.fromEvent(event));

                    // 8-2. 동일 일정의 매칭된 ACTION_ITEM 결과를 바로 다음에 추가
                    actionItemsByEventId
                            .getOrDefault(event.getEventId(), List.of())
                            .stream()
                            .map(actionItem ->
                                    EventSearchResponse.Result.fromActionItem(
                                            event,
                                            actionItem
                                    )
                            )
                            .forEach(results::add);
                });

        // 9. 준비/실행 항목만 매칭된 부모 일정을 날짜가 가까운 순으로 정렬
        List<Events> actionItemOnlyMatchedEvents =
                matchedActionItems.stream()
                        .map(ActionItems::getParentEvent)
                        .filter(event ->
                                !titleMatchedEventIds.contains(event.getEventId())
                        )
                        .collect(java.util.stream.Collectors.toMap(
                                Events::getEventId,
                                event -> event,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new
                        ))
                        .values()
                        .stream()
                        .sorted(eventComparator)
                        .toList();

        // 10. 준비/실행 항목만 매칭된 경우 ACTION_ITEM 결과만 추가
        actionItemOnlyMatchedEvents.forEach(event ->
                actionItemsByEventId
                        .getOrDefault(event.getEventId(), List.of())
                        .stream()
                        .map(actionItem ->
                                EventSearchResponse.Result.fromActionItem(
                                        event,
                                        actionItem
                                )
                        )
                        .forEach(results::add)
        );

        // 11. 정규화된 검색어와 평면 검색 결과 반환
        return EventSearchResponse.of(keyword, results);
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

    /**
     * B107 검색어를 정규화하고 유효성을 검증합니다.
     *
     * @param keywordValue 원본 검색 키워드
     * @return 앞뒤 공백이 제거된 검색 키워드
     */
    private String normalizeSearchKeyword(String keywordValue) {
        if (keywordValue == null) {
            throw new BusinessException(EventErrorCode.B107_EVENT_SEARCH_400);
        }

        String keyword = keywordValue.trim();

        if (keyword.isBlank()) {
            throw new BusinessException(EventErrorCode.B107_EVENT_SEARCH_400);
        }

        return keyword;
    }

    /**
     * B107 검색 결과의 부모 일정을 오늘과 가까운 날짜 순으로 정렬합니다.
     *
     * 날짜가 동일하면 시작 시간이 빠른 일정을 우선하고,
     * 날짜가 없는 일정은 검색 결과 그룹 하단에 배치합니다.
     *
     * @param today 서비스 기준 오늘 날짜
     * @return 일정 검색 결과 정렬 기준
     */
    private Comparator<Events> eventSearchComparator(LocalDate today) {
        return Comparator
                .comparingLong((Events event) ->
                        distanceFromToday(event.getStartDate(), today)
                )
                .thenComparing(event ->
                        event.getStartDate() == null
                                ? LocalDate.MAX
                                : event.getStartDate()
                )
                .thenComparing(event ->
                        event.getStartDatetime() == null
                                ? LocalDateTime.MAX
                                : event.getStartDatetime()
                )
                .thenComparing(Events::getEventId);
    }

    private long distanceFromToday(LocalDate startDate, LocalDate today) {
        if (startDate == null) {
            return Long.MAX_VALUE;
        }

        return Math.abs(ChronoUnit.DAYS.between(today, startDate));
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
