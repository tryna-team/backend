package com.tryna.domain.reminder.entity;

import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmReminderQueueOutbox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "reminder_id", nullable = false)
    private Long reminderId;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    public static AlarmReminderQueueOutbox create(Long reminderId, LocalDateTime scheduledAt) {
        AlarmReminderQueueOutbox outbox = new AlarmReminderQueueOutbox();
        outbox.reminderId = reminderId;
        outbox.scheduledAt = scheduledAt;
        return outbox;
    }
}
