package com.tryna.domain.event.entity;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.RecurrenceDayOfWeek;
import com.tryna.domain.event.enums.RecurrenceType;
import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.external.entity.ExternalCalendars;
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
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "events",
        indexes = {
                @Index(name = "idx_events_external_calendar_id", columnList = "external_calendar_id"),
                @Index(name = "idx_events_start_date_time", columnList = "start_date, start_datetime"),
                @Index(
                        name = "uq_events_external_calendar_event_active",
                        columnList = "external_calendar_id, external_event_id",
                        unique = true,
                        options = "WHERE source_type = 'EXTERNAL_CALENDAR' AND deleted_at IS NULL"
                )
        }
)
@Check(constraints = "deleted_at IS NOT NULL OR source_type <> 'EXTERNAL_CALENDAR' OR (external_calendar_id IS NOT NULL AND external_event_id IS NOT NULL)")@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Events extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "external_calendar_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExternalCalendars externalCalendar;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "start_datetime")
    private LocalDateTime startDatetime;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "end_datetime")
    private LocalDateTime endDatetime;

    @Column(name = "is_all_day", nullable = false)
    @ColumnDefault("false")
    private Boolean isAllDay = false;

    @Column(name = "is_recurring", nullable = false)
    @ColumnDefault("false")
    private Boolean isRecurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, length = 50)
    @ColumnDefault("'NONE'")
    private RecurrenceType recurrenceType = RecurrenceType.NONE;

    @Column(name = "recurrence_interval")
    private Integer recurrenceInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_day_of_week", nullable = false, length = 50)
    @ColumnDefault("'NONE'")
    private RecurrenceDayOfWeek recurrenceDayOfWeek = RecurrenceDayOfWeek.NONE;

    @Column(name = "recurrence_day_of_month")
    private Integer recurrenceDayOfMonth;

    @Column(name = "recurrence_end_date")
    private LocalDateTime recurrenceEndDate;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "event_type_candidate", length = 50)
    private String eventTypeCandidate;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 50)
    @ColumnDefault("'CONFIRMED'")
    private EventStatus eventStatus = EventStatus.CONFIRMED;

    @Column(name = "external_event_id", length = 255)
    private String externalEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 50)
    private Provider provider;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Events createInternalEvent(
            String sourceText,
            String title,
            String description,
            LocalDate startDate,
            LocalDateTime startDatetime,
            LocalDate endDate,
            LocalDateTime endDatetime,
            Boolean isAllDay,
            Boolean isRecurring,
            RecurrenceType recurrenceType,
            Integer recurrenceInterval,
            RecurrenceDayOfWeek recurrenceDayOfWeek,
            Integer recurrenceDayOfMonth,
            LocalDateTime recurrenceEndDate,
            String location,
            String eventType,
            EventStatus eventStatus
    ) {
        Events event = new Events();
        event.sourceText = sourceText;
        event.title = title;
        event.description = description;
        event.startDate = startDate;
        event.startDatetime = startDatetime;
        event.endDate = endDate;
        event.endDatetime = endDatetime;
        event.isAllDay = isAllDay;
        event.isRecurring = isRecurring;
        event.recurrenceType = recurrenceType;
        event.recurrenceInterval = recurrenceInterval;
        event.recurrenceDayOfWeek = recurrenceDayOfWeek;
        event.recurrenceDayOfMonth = recurrenceDayOfMonth;
        event.recurrenceEndDate = recurrenceEndDate;
        event.location = location;
        event.eventType = eventType;
        event.sourceType = SourceType.USER_NATURAL_LANGUAGE;
        event.eventStatus = eventStatus;
        return event;
    }


    // 동기화 및 자체 삭제를 위한 Soft Delete 메서드
    public void deleteSoft() {
        this.deletedAt = LocalDateTime.now();
    }

    // B105 외부 캘린더 동기화 시 업데이트를 위한 메서드
    public void updateExternalEvent(String title, String description, String location, Boolean isAllDay, LocalDate startDate, LocalDateTime startDatetime, LocalDate endDate, LocalDateTime endDatetime) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.isAllDay = isAllDay;
        this.startDate = startDate;
        this.startDatetime = startDatetime;
        this.endDate = endDate;
        this.endDatetime = endDatetime;
    }

    public void truncateRecurrenceEndDate(LocalDateTime recurrenceEndDate) {
        this.recurrenceEndDate = recurrenceEndDate;
    }

    public static Events createExternalEvent(
            ExternalCalendars externalCalendar,
            String externalEventId,
            String title,
            String description,
            String location,
            Boolean isAllDay,
            LocalDate startDate,
            LocalDateTime startDatetime,
            LocalDate endDate,
            LocalDateTime endDatetime
    ) {
        Events event = new Events();
        event.externalCalendar = externalCalendar;
        event.sourceType = SourceType.EXTERNAL_CALENDAR;
        event.externalEventId = externalEventId;
        event.provider = Provider.GOOGLE;
        event.title = title;
        event.description = description;
        event.location = location;
        event.isAllDay = isAllDay;
        event.startDate = startDate;
        event.startDatetime = startDatetime;
        event.endDate = endDate;
        event.endDatetime = endDatetime;
        event.eventStatus = EventStatus.CONFIRMED;
        return event;
    }
}
