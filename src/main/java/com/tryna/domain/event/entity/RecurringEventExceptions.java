package com.tryna.domain.event.entity;

import com.tryna.domain.event.enums.RecurringEventExceptionType;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "recurring_event_exceptions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_recurring_event_exceptions_event_date_type",
                        columnNames = {"event_id", "occurrence_date", "exception_type"}
                )
        },
        indexes = {
                @Index(name = "idx_recurring_event_exceptions_event_date", columnList = "event_id, occurrence_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringEventExceptions extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recurring_event_exception_id")
    private Long recurringEventExceptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Events event;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 50)
    private RecurringEventExceptionType exceptionType;

    public static RecurringEventExceptions createDeletedOccurrence(
            Events event,
            LocalDate occurrenceDate
    ) {
        RecurringEventExceptions exception = new RecurringEventExceptions();
        exception.event = event;
        exception.occurrenceDate = occurrenceDate;
        exception.exceptionType = RecurringEventExceptionType.DELETED;
        return exception;
    }
}
