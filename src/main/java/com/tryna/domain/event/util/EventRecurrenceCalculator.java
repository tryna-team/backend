package com.tryna.domain.event.util;

import com.tryna.domain.event.entity.Events;

import java.time.LocalDate;

/**
 * 반복 일정의 "다음 회차 날짜"를 계산하는 유틸리티.
 *
 * 리마인더가 발송된 직후, 반복 일정/반복 일정에 연결된 준비·실행 항목의 알람을
 * 다음 주기로 재스케줄링하기 위해 사용한다. (요청 검증용 {@code isRecurringOccurrenceOn} 계열 로직과는
 * 별개로, 이미 유효했던 "현재 회차 날짜"를 기준으로 다음 회차만 직접 계산하는 경량 버전이다.)
 */
public final class EventRecurrenceCalculator {

    private EventRecurrenceCalculator() {
    }

    /**
     * @param event                 반복 규칙을 가진 일정
     * @param currentOccurrenceDate 방금 알람이 발송된 회차의 날짜
     * @return 다음 회차 날짜. 반복이 아니거나, 반복 종료일을 지난 경우 null
     */
    public static LocalDate nextOccurrenceDateAfter(Events event, LocalDate currentOccurrenceDate) {
        if (event == null
                || currentOccurrenceDate == null
                || !Boolean.TRUE.equals(event.getIsRecurring())
                || event.getRecurrenceType() == null) {
            return null;
        }

        int interval = event.getRecurrenceInterval() == null
                ? 1
                : Math.max(1, event.getRecurrenceInterval());

        LocalDate next = switch (event.getRecurrenceType()) {
            case DAILY -> currentOccurrenceDate.plusDays(interval);
            case WEEKLY -> currentOccurrenceDate.plusWeeks(interval);
            case MONTHLY -> nextMonthly(event, currentOccurrenceDate, interval);
            case YEARLY -> nextYearly(event, currentOccurrenceDate, interval);
            case NONE, CUSTOM -> null;
        };

        if (next == null) {
            return null;
        }

        if (event.getRecurrenceEndDate() != null && next.isAfter(event.getRecurrenceEndDate().toLocalDate())) {
            return null;
        }

        return next;
    }

    private static LocalDate nextMonthly(Events event, LocalDate current, int interval) {
        LocalDate base = current.plusMonths(interval);
        Integer dayOfMonth = event.getRecurrenceDayOfMonth();

        if (dayOfMonth != null) {
            int clampedDay = Math.min(dayOfMonth, base.lengthOfMonth());
            base = base.withDayOfMonth(clampedDay);
        }

        return base;
    }

    private static LocalDate nextYearly(Events event, LocalDate current, int interval) {
        LocalDate base = current.plusYears(interval);
        Integer dayOfMonth = event.getRecurrenceDayOfMonth();

        if (dayOfMonth != null) {
            int clampedDay = Math.min(dayOfMonth, base.lengthOfMonth());
            base = base.withDayOfMonth(clampedDay);
        }

        return base;
    }
}
