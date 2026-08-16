package com.tryna.domain.event.service;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.repository.EventsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.util.CompatibilityHints;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidaySyncService {

    private final EventsRepository eventsRepository;

    // 애플 캘린더 URL
    private static final String APPLE_HOLIDAY_URL = "https://calendars.icloud.com/holidays/kr_ko.ics";

    // 네트워크 타임아웃 설정 (10초)
    private static final int TIMEOUT_MILLIS = 10_000;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncAppleKoreanHolidays() {
        System.setProperty("net.fortuna.ical4j.parser.relaxed", "true");
        System.setProperty("net.fortuna.ical4j.relaxed.parsing.enabled", "true");
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);

        log.info("애플 공휴일 데이터 초기화 및 주기적 동기화(Sync)를 시작합니다.");

        try {
            URLConnection connection = new URL(APPLE_HOLIDAY_URL).openConnection();
            // 타임아웃 지정 (무한 대기 방지)
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);

            // 브라우저인 척 위장 (애플 서버 차단 방지)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");

            InputStream rawStream = connection.getInputStream();
            String encoding = connection.getContentEncoding();

            // 애플 서버가 데이터를 GZIP으로 압축해서 보냈을 경우 압축 해제 처리
            InputStream finalStream = (encoding != null && encoding.equalsIgnoreCase("gzip"))
                    ? new GZIPInputStream(rawStream)
                    : rawScaleStreamCheck(rawStream); // 안전한 스트림 처리

            List<ParsedHolidayDto> parsedHolidays = new ArrayList<>();
            try (InputStream is = finalStream) {
                CalendarBuilder builder = new CalendarBuilder();
                Calendar calendar = builder.build(is);

                // 1. 공휴일 탐색 범위를 서비스 전체 범위로 확장
                java.util.Calendar calStart = java.util.Calendar.getInstance();
                calStart.set(1970, java.util.Calendar.JANUARY, 1);
                java.util.Calendar calEnd = java.util.Calendar.getInstance();
                calEnd.set(2100, java.util.Calendar.DECEMBER, 31);

                net.fortuna.ical4j.model.DateTime startPeriod = new net.fortuna.ical4j.model.DateTime(calStart.getTime());
                net.fortuna.ical4j.model.DateTime endPeriod = new net.fortuna.ical4j.model.DateTime(calEnd.getTime());
                net.fortuna.ical4j.model.Period searchPeriod = new net.fortuna.ical4j.model.Period(startPeriod, endPeriod);

                log.info("애플 공휴일 캘린더 파싱 시작...");

                for (Object component : calendar.getComponents(Component.VEVENT)) {
                    VEvent event = (VEvent) component;

                    // 필수값이 없는 데이터는 건너뛰기
                    if (event.getUid() == null || event.getSummary() == null || event.getStartDate() == null) {
                        continue;
                    }

                    String baseUid = event.getUid().getValue();
                    String title = event.getSummary().getValue();

                    // 2. RRULE(반복 규칙)을 분석하여 검색 기간 내의 모든 날짜로 펼치기 (단일 일정도 1개로 정상 반환됨)
                    net.fortuna.ical4j.model.PeriodList occurrences = event.calculateRecurrenceSet(searchPeriod);

                    for (Object p : occurrences) {
                        net.fortuna.ical4j.model.Period occurrence = (net.fortuna.ical4j.model.Period) p;

                        // ical4j의 날짜 포맷(yyyyMMdd 또는 yyyyMMddTHHmmss)에서 날짜 8자리만 추출
                        String dateStr = occurrence.getStart().toString().substring(0, 8);

                        try {
                            LocalDate occurrenceDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));

                            // 3. 기존 UID에 연도를 붙여 DB에서 각각 독립적인 공휴일로 인식되도록 처리
                            String uniqueUid = baseUid + "-" + occurrenceDate.getYear();

                            parsedHolidays.add(new ParsedHolidayDto(title, occurrenceDate, uniqueUid));
                        } catch (Exception e) {
                            // 날짜 파싱이 실패하면 해당 건만 조용히 넘김
                            log.debug("공휴일 날짜 파싱 실패 (무시됨): {}", dateStr);
                        }
                    }
                }
            }

            // DB 저장 및 동시성 충돌 방지 로직 호출
            saveHolidaysAtomically(parsedHolidays);

        } catch (Exception e) {
            log.error("공휴일 동기화 중 오류 발생", e);
        }
    }

    private InputStream rawScaleStreamCheck(InputStream rawStream) {
        return rawStream;
    }

    /**
     * 개별 건별 존재 여부 확인 및 저장을 트랜잭션 단위로 안전하게 처리하여
     * 다중 서버 환경(동시성 이슈)에서도 중복 삽입을 원천 방어합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveHolidaysAtomically(List<ParsedHolidayDto> parsedHolidays) {
        int savedCount = 0;
        for (ParsedHolidayDto dto : parsedHolidays) {
            // 원자적(Atomic) 방어: 존재하지 않는 경우에만 안전하게 삽입
            if (!eventsRepository.existsHolidayByExternalEventId(dto.uid())) {
                try {
                    Events holidayEvent = Events.createHolidayEvent(dto.title(), dto.startDate(), dto.uid());
                    eventsRepository.save(holidayEvent);
                    savedCount++;
                } catch (Exception e) {
                    // 동시 요청으로 인해 유니크 충돌 등이 발생할 경우 무시 (Race Condition 방어)
                    log.debug("공휴일 동기화 중 중복 삽입 충돌 무시: uid={}", dto.uid());
                }
            }
        }

        if (savedCount > 0) {
            log.info("새로운 공휴일 {}건 동기화 완료", savedCount);
        } else {
            log.info("추가할 새로운 공휴일이 없습니다.");
        }
    }

    private record ParsedHolidayDto(
            String title,
            LocalDate startDate,
            String uid
    ) {
    }
}