package com.tryna.domain.reminder.repository;

import com.tryna.domain.reminder.entity.AlarmReminderQueueOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmReminderQueueOutboxRepository extends JpaRepository<AlarmReminderQueueOutbox, Long> {
}
