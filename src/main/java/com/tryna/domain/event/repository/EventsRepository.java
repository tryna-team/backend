package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.external.enums.ConnectionStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EventsRepository extends JpaRepository<Events, Long> {

    boolean existsByEventIdAndEventStatusIn(
            Long eventId,
            Collection<EventStatus> eventStatuses
    );

    @Query("""
            SELECT e
              FROM Events e
              LEFT JOIN e.externalCalendar ec
              LEFT JOIN ec.connection ecc
             WHERE e.eventId = :eventId
               AND e.eventStatus IN :eventStatuses
               AND (
                    EXISTS (
                        SELECT 1
                          FROM UserEvents ue
                         WHERE ue.event = e
                           AND ue.user.userId = :userId
                    )
                    OR (
                        ecc.user.userId = :userId
                        AND ecc.connectionStatus = :connectionStatus
                    )
               )
            """)
    Optional<Events> findVisibleEventAccessibleToUser(
            @Param("userId") Long userId,
            @Param("eventId") Long eventId,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses,
            @Param("connectionStatus") ConnectionStatus connectionStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Events e
               SET e.deletedAt = :deletedAt
             WHERE e.eventId = :eventId
               AND e.deletedAt IS NULL
            """)
    int softDeleteById(
            @Param("eventId") Long eventId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
