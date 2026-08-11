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

        @EventListener(ApplicationReadyEvent.class)
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void syncAppleKoreanHolidays() {
            System.setProperty("net.fortuna.ical4j.parser.relaxed", "true");
            System.setProperty("net.fortuna.ical4j.relaxed.parsing.enabled", "true");
            CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
            CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);

            log.info("서버 기동 완료! 애플 공휴일 데이터 초기화(Sync)를 시작합니다.");

            try {
                URLConnection connection = new URL(APPLE_HOLIDAY_URL).openConnection();
                // 브라우저인 척 위장 (애플 서버 차단 방지)
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");

                InputStream rawStream = connection.getInputStream();
                String encoding = connection.getContentEncoding();

                // 애플 서버가 데이터를 GZIP으로 압축해서 보냈을 경우 압축 해제 처리
                InputStream finalStream = (encoding != null && encoding.equalsIgnoreCase("gzip"))
                        ? new GZIPInputStream(rawStream)
                        : rawStream;

                try (InputStream is = finalStream) {
                    CalendarBuilder builder = new CalendarBuilder();
                    Calendar calendar = builder.build(is);

                    log.info("애플 공휴일 캘린더 파싱 시작...");
                    List<Events> newHolidays = new ArrayList<>();

                    for (Object component : calendar.getComponents(Component.VEVENT)) {
                        VEvent event = (VEvent) component;

                        // 필수값이 없는 데이터는 건너뛰기
                        if (event.getUid() == null || event.getSummary() == null || event.getStartDate() == null) {
                            continue;
                        }

                        String uid = event.getUid().getValue();
                        String title = event.getSummary().getValue();
                        String dateStr = event.getStartDate().getValue();

                        LocalDate startDate;
                        try {
                            // 공휴일 포맷인 yyyyMMdd 만 파싱 시도
                            startDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                        } catch (Exception e) {
                            continue; // 날짜 포맷이 이상하면 조용히 무시하고 다음 일정으로
                        }

                        if (!eventsRepository.existsHolidayByExternalEventId(uid)) {
                            // 생성 팩토리 호출
                            Events holidayEvent = Events.createHolidayEvent(title, startDate, uid);
                            newHolidays.add(holidayEvent);
                        }
                    }

                    if (!newHolidays.isEmpty()) {
                        eventsRepository.saveAll(newHolidays);
                        log.info("새로운 공휴일 {}건 동기화 완료", newHolidays.size());
                    } else {
                        log.info("추가할 새로운 공휴일이 없습니다.");
                    }
                }
            } catch (Exception e) {
                log.error("공휴일 동기화 중 오류 발생", e);
            }
        }
    }