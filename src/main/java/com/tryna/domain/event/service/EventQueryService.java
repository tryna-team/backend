package com.tryna.domain.event.service;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.repository.ActionItemsRepository;
import com.tryna.domain.event.dto.*;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.RecurringEventExceptionType;
import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.event.repository.EventsRepository;
import com.tryna.domain.event.repository.RecurringEventExceptionsRepository;
import com.tryna.domain.event.repository.UserEventsRepository;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.BusinessException;
import com.tryna.global.exception.CommonErrorCode;
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
    private final RecurringEventExceptionsRepository recurringEventExceptionsRepository;

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

    /**
     * 제공된 연도에 사용자가 볼 수 있는(표시되는) 일정이 있는지 확인
     * 저장된 일정과 해당 범위 내에서 발생할 수 있는 반복 일정을 모두 고려합니다.
     */
    public boolean hasEventsInYear(Long userId, Integer year) {
        validateUserId(userId);

        if (year == null || year < MIN_YEAR || year > MAX_YEAR) {
            return false;
        }

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        List<Object[]> rows = userEventsRepository.countEventsByDate(
                userId,
                startDate,
                endDate,
                VISIBLE_EVENT_STATUSES
        );

        if (!rows.isEmpty()) {
            return rows.stream().anyMatch(r -> ((Long) r[1]) > 0);
        }

        List<UserEvents> recurring = userEventsRepository.findRecurringUserEventsInRange(
                userId,
                startDate.atStartOfDay(),
                endDate,
                VISIBLE_EVENT_STATUSES
        );

        return !recurring.isEmpty();
    }

    private CalendarMonthlyResponse buildMonthlyCalendar(Long userId, Integer year, Integer month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        Map<LocalDate, List<EventOccurrence>> occurrencesByDate = getOccurrencesByDate(userId, startDate, endDate);

        List<CalendarMonthlyResponse.DayEventCount> days = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            List<EventOccurrence> occurrences = occurrencesByDate.getOrDefault(date, List.of());
            List<CalendarDateEventsResponse.EventSummary> previewEvents = occurrences.stream()
                    .sorted(eventOccurrenceComparator())
                    .map(this::toEventSummary)
                    .toList();
            days.add(new CalendarMonthlyResponse.DayEventCount(
                    date,
                    (long) previewEvents.size(),
                    !previewEvents.isEmpty(),
                    previewEvents
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
        List<UserEvents> directUserEvents = userEventsRepository.findUserEventsByDate(
                userId,
                date,
                VISIBLE_EVENT_STATUSES
        );

        List<UserEvents> recurringUserEvents = userEventsRepository.findRecurringUserEventsInRange(
                userId,
                date.atStartOfDay(),
                date,
                VISIBLE_EVENT_STATUSES
        );

        List<EventOccurrence> occurrences = new ArrayList<>(directUserEvents.stream()
                .map(userEvent -> new EventOccurrence(
                        userEvent.getEvent(),
                        userEvent.getEvent().getStartDate(),
                        resolveLabelId(userEvent)
                ))
                .toList());

        List<EventOccurrence> recurringOccurrences = recurringUserEvents.stream()
                .flatMap(userEvent -> resolveAdditionalRecurringOccurrencesCoveringDate(userEvent, date).stream())
                .toList();

        occurrences.addAll(recurringOccurrences);

        Set<DeletedOccurrenceKey> deletedOccurrences = findDeletedOccurrenceKeys(occurrences);

        List<CalendarDateEventsResponse.EventSummary> events = occurrences.stream()
                .filter(occurrence -> !isDeletedOccurrence(
                        occurrence.event(),
                        occurrence.occurrenceDate(),
                        deletedOccurrences
                ))
                .sorted(eventOccurrenceComparator())
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

        // 5-1. 검색 결과에 포함될 일정들의 라벨 ID를 한 번에 조회
        Set<Long> resultEventIds = new HashSet<>(titleMatchedEventIds);
        matchedActionItems.stream()
                .map(ActionItems::getParentEvent)
                .map(Events::getEventId)
                .forEach(resultEventIds::add);
        Map<Long, Long> labelIdsByEventId = findLabelIdsByEventId(userId, resultEventIds);

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
                    results.add(EventSearchResponse.Result.fromEvent(
                            event,
                            labelIdsByEventId.get(event.getEventId())
                    ));

                    // 8-2. 동일 일정의 매칭된 ACTION_ITEM 결과를 바로 다음에 추가
                    actionItemsByEventId
                            .getOrDefault(event.getEventId(), List.of())
                            .stream()
                            .map(actionItem ->
                                    EventSearchResponse.Result.fromActionItem(
                                            event,
                                            actionItem,
                                            labelIdsByEventId.get(event.getEventId())
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
                                        actionItem,
                                        labelIdsByEventId.get(event.getEventId())
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
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_403));

        Long labelId = userEventsRepository.findLabelIdByUserIdAndEventId(userId, eventId)
                .orElse(null);

        return toEventDetailResponse(event, labelId);
    }

    private Map<LocalDate, List<EventOccurrence>> getOccurrencesByDate(Long userId, LocalDate startDate, LocalDate endDate) {
        List<UserEvents> directUserEvents = userEventsRepository.findUserEventsOverlappingRange(
                userId,
                startDate,
                endDate,
                VISIBLE_EVENT_STATUSES
        );

        Map<LocalDate, List<EventOccurrence>> occurrencesByDate = new HashMap<>();
        List<EventOccurrence> occurrences = new ArrayList<>(directUserEvents.stream()
                .map(userEvent -> new EventOccurrence(
                        userEvent.getEvent(),
                        userEvent.getEvent().getStartDate(),
                        resolveLabelId(userEvent)
                ))
                .toList());

        List<UserEvents> recurringUserEvents = userEventsRepository.findRecurringUserEventsInRange(
                userId,
                startDate.atStartOfDay(),
                endDate,
                VISIBLE_EVENT_STATUSES
        );

        for (UserEvents userEvent : recurringUserEvents) {
            Events event = userEvent.getEvent();
            Long labelId = resolveLabelId(userEvent);
            LocalDate occurrenceSearchStart = startDate.minusDays(eventDurationDays(event));
            for (LocalDate occurrenceDate : resolveRecurringOccurrenceDates(event, occurrenceSearchStart, endDate)) {
                if (occurrenceDate.equals(event.getStartDate())) {
                    continue;
                }
                occurrences.add(new EventOccurrence(event, occurrenceDate, labelId));
            }
        }

        Set<DeletedOccurrenceKey> deletedOccurrences = findDeletedOccurrenceKeys(occurrences);
        for (EventOccurrence occurrence : occurrences) {
            if (isDeletedOccurrence(
                    occurrence.event(),
                    occurrence.occurrenceDate(),
                    deletedOccurrences
            )) {
                continue;
            }
            addOccurrenceToDates(occurrencesByDate, occurrence, startDate, endDate);
        }

        return occurrencesByDate;
    }

    private List<LocalDate> resolveRecurringOccurrenceDates(Events event, LocalDate rangeStart, LocalDate rangeEnd) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = rangeStart.isAfter(event.getStartDate()) ? rangeStart : event.getStartDate();

        while (!current.isAfter(rangeEnd)) {
            if (isRecurringOccurrenceOn(event, current)) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }

        return dates;
    }

    private List<EventOccurrence> resolveAdditionalRecurringOccurrencesCoveringDate(UserEvents userEvent, LocalDate date) {
        Events event = userEvent.getEvent();
        Long labelId = resolveLabelId(userEvent);
        LocalDate occurrenceSearchStart = date.minusDays(eventDurationDays(event));
        return resolveRecurringOccurrenceDates(event, occurrenceSearchStart, date)
                .stream()
                .filter(occurrenceDate -> !occurrenceDate.equals(event.getStartDate()))
                .filter(occurrenceDate -> occurrenceCoversDate(event, occurrenceDate, date))
                .map(occurrenceDate -> new EventOccurrence(event, occurrenceDate, labelId))
                .toList();
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
                YearMonth.from(event.getStartDate()),
                YearMonth.from(date)
        );
        return months % interval == 0;
    }

    private boolean isYearlyOccurrence(Events event, LocalDate date, int interval) {
        Integer expectedDayOfMonth = event.getRecurrenceDayOfMonth();
        if (expectedDayOfMonth == null
                || date.getMonthValue() != event.getStartDate().getMonthValue()
                || date.getDayOfMonth() != expectedDayOfMonth) {
            return false;
        }

        long years = ChronoUnit.YEARS.between(event.getStartDate(), date);
        return years % interval == 0;
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

    private Set<DeletedOccurrenceKey> findDeletedOccurrenceKeys(
            Collection<Long> eventIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (eventIds.isEmpty()) {
            return Set.of();
        }

        Set<DeletedOccurrenceKey> deletedOccurrences = new HashSet<>();
        for (Object[] row : recurringEventExceptionsRepository.findExceptionKeysInRange(
                eventIds,
                startDate,
                endDate,
                RecurringEventExceptionType.DELETED
        )) {
            deletedOccurrences.add(new DeletedOccurrenceKey((Long) row[0], (LocalDate) row[1]));
        }
        return deletedOccurrences;
    }

    private Set<DeletedOccurrenceKey> findDeletedOccurrenceKeys(List<EventOccurrence> occurrences) {
        if (occurrences.isEmpty()) {
            return Set.of();
        }

        List<Long> eventIds = occurrences.stream()
                .map(occurrence -> occurrence.event().getEventId())
                .distinct()
                .toList();
        LocalDate startDate = occurrences.stream()
                .map(EventOccurrence::occurrenceDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate endDate = occurrences.stream()
                .map(EventOccurrence::occurrenceDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        if (startDate == null || endDate == null) {
            return Set.of();
        }

        return findDeletedOccurrenceKeys(eventIds, startDate, endDate);
    }

    private boolean isDeletedOccurrence(
            Events event,
            LocalDate occurrenceDate,
            Set<DeletedOccurrenceKey> deletedOccurrences
    ) {
        return deletedOccurrences.contains(new DeletedOccurrenceKey(event.getEventId(), occurrenceDate));
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

    private CalendarDateEventsResponse.EventSummary toEventSummary(EventOccurrence occurrence) {
        Events event = occurrence.event();
        LocalDate occurrenceDate = occurrence.occurrenceDate();
        return new CalendarDateEventsResponse.EventSummary(
                event.getEventId(),
                event.getTitle(),
                occurrenceDate,
                formatTime(event.getStartDatetime()),
                resolveOccurrenceEndDate(event, occurrenceDate),
                formatTime(event.getEndDatetime()),
                event.getIsAllDay(),
                event.getLocation(),
                occurrence.labelId(),
                event.getSourceType(),
                event.getEventStatus()
        );
    }

    private LocalDate resolveOccurrenceEndDate(Events event, LocalDate occurrenceDate) {
        if (event.getStartDate() == null || event.getEndDate() == null || occurrenceDate == null) {
            return event.getEndDate();
        }

        long durationDays = ChronoUnit.DAYS.between(event.getStartDate(), event.getEndDate());
        return occurrenceDate.plusDays(durationDays);
    }

    private long eventDurationDays(Events event) {
        if (event.getStartDate() == null || event.getEndDate() == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(event.getStartDate(), event.getEndDate()));
    }

    private boolean occurrenceCoversDate(Events event, LocalDate occurrenceDate, LocalDate date) {
        LocalDate occurrenceEndDate = occurrenceDate.plusDays(eventDurationDays(event));
        return !date.isBefore(occurrenceDate)
                && !date.isAfter(occurrenceEndDate);
    }

    private Long resolveLabelId(UserEvents userEvent) {
        if (userEvent.getLabel() == null) {
            return null;
        }
        return userEvent.getLabel().getLabelId();
    }

    private Map<Long, Long> findLabelIdsByEventId(Long userId, Set<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> labelIdsByEventId = new HashMap<>();
        for (Object[] row : userEventsRepository.findLabelIdsByUserIdAndEventIds(userId, eventIds)) {
            labelIdsByEventId.put((Long) row[0], (Long) row[1]);
        }
        return labelIdsByEventId;
    }

    private void addOccurrenceToDates(
            Map<LocalDate, List<EventOccurrence>> occurrencesByDate,
            EventOccurrence occurrence,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        LocalDate occurrenceStartDate = occurrence.occurrenceDate();
        LocalDate occurrenceEndDate = resolveOccurrenceEndDate(occurrence.event(), occurrenceStartDate);
        if (occurrenceEndDate == null) {
            occurrenceEndDate = occurrenceStartDate;
        }

        LocalDate current = occurrenceStartDate.isBefore(rangeStart) ? rangeStart : occurrenceStartDate;
        LocalDate end = occurrenceEndDate.isAfter(rangeEnd) ? rangeEnd : occurrenceEndDate;

        while (!current.isAfter(end)) {
            occurrencesByDate.computeIfAbsent(current, ignored -> new ArrayList<>()).add(occurrence);
            current = current.plusDays(1);
        }
    }

    private Comparator<EventOccurrence> eventOccurrenceComparator() {
        return Comparator
                .comparing((EventOccurrence occurrence) -> occurrence.event().getStartDatetime() == null)
                .thenComparing(
                        occurrence -> occurrence.event().getStartDatetime(),
                        Comparator.nullsLast(LocalDateTime::compareTo)
                )
                .thenComparing(
                        occurrence -> occurrence.event().getCreatedAt(),
                        Comparator.nullsLast(LocalDateTime::compareTo)
                )
                .thenComparing(occurrence -> occurrence.event().getEventId());
    }

    private record EventOccurrence(
            Events event,
            LocalDate occurrenceDate,
            Long labelId
    ) {
    }

    private record DeletedOccurrenceKey(
            Long eventId,
            LocalDate occurrenceDate
    ) {
    }

    private EventDetailResponse toEventDetailResponse(Events event, Long labelId) {
        return new EventDetailResponse(
                event.getEventId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartDate(),
                formatTime(event.getStartDatetime()),
                event.getEndDate(),
                formatTime(event.getEndDatetime()),
                event.getIsAllDay(),
                event.getIsRecurring(),
                event.getRecurrenceType(),
                event.getRecurrenceInterval(),
                event.getRecurrenceDayOfWeek(),
                event.getRecurrenceDayOfMonth(),
                event.getRecurrenceEndDate(),
                event.getLocation(),
                labelId,
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
