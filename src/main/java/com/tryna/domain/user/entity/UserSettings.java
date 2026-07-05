package com.tryna.domain.user.entity;

import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "user_settings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_settings_user_id", columnNames = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_setting_id")
    private Long userSettingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Users user;

    @Column(name = "is_all_notification_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isAllNotificationEnabled = true;

    @Column(name = "is_event_reminder_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isEventReminderEnabled = true;

    @Column(name = "is_action_item_reminder_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isActionItemReminderEnabled = true;

    @Column(name = "default_all_day_event_time", nullable = false, columnDefinition = "TIME DEFAULT '09:00:00'")
    private LocalTime defaultAllDayEventTime = LocalTime.of(9, 0);

    @Column(name = "default_action_item_time", nullable = false, columnDefinition = "TIME DEFAULT '09:00:00'")
    private LocalTime defaultActionItemTime = LocalTime.of(9, 0);

    @Column(name = "default_event_reminder_offset_minutes", nullable = false, columnDefinition = "INTEGER DEFAULT 30")
    private Integer defaultEventReminderOffsetMinutes = 30;

    @Column(name = "is_feedback_data_collected", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean isFeedbackDataCollected = false;

    @Column(name = "is_personalization_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isPersonalizationEnabled = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

}
