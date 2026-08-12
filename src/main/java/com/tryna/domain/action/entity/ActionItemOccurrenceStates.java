package com.tryna.domain.action.entity;

import com.tryna.domain.action.enums.ActionItemStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "action_item_occurrence_states",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_action_item_occurrence_states_item_date",
                        columnNames = {"action_item_id", "occurrence_date"}
                )
        },
        indexes = {
                @Index(name = "idx_action_item_occurrence_states_date", columnList = "occurrence_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionItemOccurrenceStates extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_item_occurrence_state_id")
    private Long actionItemOccurrenceStateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_item_id", nullable = false)
    private ActionItems actionItem;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_item_status", nullable = false, length = 50)
    @ColumnDefault("'PENDING'")
    private ActionItemStatus actionItemStatus = ActionItemStatus.PENDING;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static ActionItemOccurrenceStates create(
            ActionItems actionItem,
            LocalDate occurrenceDate
    ) {
        ActionItemOccurrenceStates state = new ActionItemOccurrenceStates();
        state.actionItem = actionItem;
        state.occurrenceDate = occurrenceDate;
        state.actionItemStatus = ActionItemStatus.PENDING;
        return state;
    }

    public static ActionItemOccurrenceStates copyOf(
            ActionItems actionItem,
            LocalDate occurrenceDate,
            ActionItemOccurrenceStates source
    ) {
        ActionItemOccurrenceStates state = new ActionItemOccurrenceStates();
        state.actionItem = actionItem;
        state.occurrenceDate = occurrenceDate;
        state.actionItemStatus = source.actionItemStatus;
        state.completedAt = source.completedAt;
        return state;
    }

    public void updateStatus(ActionItemStatus status) {
        this.actionItemStatus = status;

        if (status == ActionItemStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
            return;
        }

        this.completedAt = null;
    }
}
