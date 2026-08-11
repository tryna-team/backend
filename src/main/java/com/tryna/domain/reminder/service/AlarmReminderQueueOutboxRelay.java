package com.tryna.domain.reminder.service;

import com.tryna.domain.reminder.repository.AlarmDelayedQueueRepository;
import com.tryna.domain.reminder.repository.AlarmReminderQueueOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
class AlarmReminderQueueOutboxRelay {

    private final AlarmReminderQueueOutboxRepository outboxRepository;
    private final AlarmDelayedQueueRepository alarmDelayedQueueRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relayAndCleanup(Long outboxId, Long reminderId, LocalDateTime scheduledAt) {
        try {
            alarmDelayedQueueRepository.schedule(reminderId, scheduledAt);
            outboxRepository.deleteById(outboxId);
        } catch (Exception e) {
            log.error("Redis 알람 큐 릴레이에 실패했습니다. outboxId={}, reminderId={}", outboxId, reminderId, e);
        }
    }
}
