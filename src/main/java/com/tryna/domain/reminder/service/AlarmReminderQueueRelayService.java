package com.tryna.domain.reminder.service;

import com.tryna.domain.reminder.entity.AlarmReminderQueueOutbox;
import com.tryna.domain.reminder.repository.AlarmReminderQueueOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmReminderQueueRelayService {

    private final AlarmReminderQueueOutboxRepository outboxRepository;
    private final AlarmReminderQueueOutboxRelay alarmReminderQueueOutboxRelay;

    public void enqueueSchedule(Long reminderId, LocalDateTime scheduledAt) {
        AlarmReminderQueueOutbox outbox = outboxRepository.save(
                AlarmReminderQueueOutbox.create(reminderId, scheduledAt));

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            alarmReminderQueueOutboxRelay.relayAndCleanup(outbox.getOutboxId(), reminderId, scheduledAt);
            return;
        }

        Long outboxId = outbox.getOutboxId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                alarmReminderQueueOutboxRelay.relayAndCleanup(outboxId, reminderId, scheduledAt);
            }
        });
    }
}
