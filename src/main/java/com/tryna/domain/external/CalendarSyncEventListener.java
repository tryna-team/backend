package com.tryna.domain.external;

import com.tryna.domain.external.service.CalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarSyncEventListener {

    private final CalendarSyncService calendarSyncService;

    @Async("calendarSyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(CalendarSyncRequestedEvent event) {
        try {
            calendarSyncService.syncGoogleCalendar(event.userId(), event.targetYear());
        } catch (Exception e) {
            log.warn("유저 {} 비동기 구글 캘린더 동기화 실패: {}", event.userId(), e.getMessage());
        }
    }
}