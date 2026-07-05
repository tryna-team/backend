package com.tryna.domain.reminder.entity;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.reminder.enums.ReminderStatus;
import com.tryna.domain.reminder.enums.TargetType;
import com.tryna.domain.user.entity.Users;
import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reminders",
        indexes = {
                @Index(name = "idx_reminders_target_event_id", columnList = "target_event_id"),
                @Index(name = "idx_reminders_target_action_item_id", columnList = "target_action_item_id"),
                @Index(name = "idx_reminders_status_scheduled", columnList = "reminder_status, scheduled_at"),
                @Index(
                        name = "uq_reminders_event_schedule",
                        columnList = "target_event_id, scheduled_at, delivery_channel",
                        unique = true,
                        options = "WHERE target_type = 'EVENT' AND target_event_id IS NOT NULL AND reminder_status = 'SCHEDULED'"
                ),
                @Index(
                        name = "uq_reminders_action_item_schedule",
                        columnList = "target_action_item_id, scheduled_at, delivery_channel",
                        unique = true,
                        options = "WHERE target_type = 'TIMED_ACTION' AND target_action_item_id IS NOT NULL AND reminder_status = 'SCHEDULED'"
                )
        }
)
@Check(constraints = "(target_type = 'EVENT' AND target_event_id IS NOT NULL AND target_action_item_id IS NULL) OR (target_type = 'TIMED_ACTION' AND target_event_id IS NULL AND target_action_item_id IS NOT NULL)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reminders extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reminder_id")
    private Long reminderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_event_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Events targetEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_action_item_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ActionItems targetActionItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private TargetType targetType;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "delivery_channel", nullable = false, length = 50)
    @ColumnDefault("'PUSH'")
    private String deliveryChannel = "PUSH";

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_status", nullable = false, length = 50)
    @ColumnDefault("'SCHEDULED'")
    private ReminderStatus reminderStatus = ReminderStatus.SCHEDULED;

    @Column(name = "alarm_title", nullable = false, length = 100)
    private String alarmTitle;

    @Column(name = "alarm_body", nullable = false, length = 255)
    private String alarmBody;

}
