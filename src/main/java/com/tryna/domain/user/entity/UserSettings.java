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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "user_settings")
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

    @Column(name = "is_all_notification_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean isAllNotificationEnabled = true;

    @Column(name = "is_event_reminder_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean isEventReminderEnabled = true;

    @Column(name = "is_action_item_reminder_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean isActionItemReminderEnabled = true;

    @Column(name = "default_all_day_event_time", nullable = false)
    @ColumnDefault("'09:00:00'")
    private LocalTime defaultAllDayEventTime = LocalTime.of(9, 0);

    @Column(name = "default_action_item_time", nullable = false)
    @ColumnDefault("'09:00:00'")
    private LocalTime defaultActionItemTime = LocalTime.of(9, 0);

    @Column(name = "default_event_reminder_offset_minutes", nullable = false)
    @ColumnDefault("30")
    private Integer defaultEventReminderOffsetMinutes = 30;

    @Column(name = "is_feedback_data_collected", nullable = false)
    @ColumnDefault("false")
    private Boolean isFeedbackDataCollected = false;

    @Column(name = "is_personalization_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean isPersonalizationEnabled = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 기본 설정 생성용 정적 팩토리 메서드
    public static UserSettings createDefault(Users user) {
        UserSettings settings = new UserSettings();
        settings.user = user;
        // 필드 초기화는 @ColumnDefault 및 초기값 선언으로 자동 처리됨
        return settings;
    }
}
