package com.tryna.domain.action.repository;

import com.tryna.domain.action.entity.ActionItemOccurrenceStates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActionItemOccurrenceStatesRepository extends JpaRepository<ActionItemOccurrenceStates, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO action_item_occurrence_states (
                action_item_id,
                occurrence_date,
                action_item_status,
                created_at,
                updated_at
            )
            VALUES (
                :actionItemId,
                :occurrenceDate,
                'PENDING',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (action_item_id, occurrence_date) DO NOTHING
            """, nativeQuery = true)
    int insertPendingStateIfAbsent(
            @Param("actionItemId") Long actionItemId,
            @Param("occurrenceDate") LocalDate occurrenceDate
    );

    Optional<ActionItemOccurrenceStates> findByActionItem_ActionItemIdAndOccurrenceDate(
            Long actionItemId,
            LocalDate occurrenceDate
    );

    List<ActionItemOccurrenceStates> findByActionItem_ActionItemIdInAndOccurrenceDate(
            Collection<Long> actionItemIds,
            LocalDate occurrenceDate
    );

    List<ActionItemOccurrenceStates> findByActionItem_ActionItemIdInAndOccurrenceDateIn(
            Collection<Long> actionItemIds,
            Collection<LocalDate> occurrenceDates
    );
}
