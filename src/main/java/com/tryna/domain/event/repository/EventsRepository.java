package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.Events;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EventsRepository extends JpaRepository<Events, Long> {

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
