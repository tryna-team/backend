package com.tryna.domain.action.repository;

import com.tryna.domain.action.entity.ActionItemOccurrenceStates;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActionItemOccurrenceStatesRepository extends JpaRepository<ActionItemOccurrenceStates, Long> {

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
