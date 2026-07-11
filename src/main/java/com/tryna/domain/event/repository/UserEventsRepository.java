package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEventsRepository extends JpaRepository<UserEvents, Long> {

    @Query("""
            SELECT e.startDate, COUNT(e.eventId)
              FROM UserEvents ue
              JOIN ue.event e
             WHERE ue.user.userId = :userId
               AND e.startDate BETWEEN :startDate AND :endDate
               AND e.eventStatus IN :eventStatuses
             GROUP BY e.startDate
            """)
    List<Object[]> countEventsByDate(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    @Query("""
            SELECT e
              FROM UserEvents ue
              JOIN ue.event e
             WHERE ue.user.userId = :userId
               AND e.startDate = :date
               AND e.eventStatus IN :eventStatuses
             ORDER BY
               CASE WHEN e.startDatetime IS NULL THEN 1 ELSE 0 END,
               e.startDatetime ASC,
               e.createdAt ASC
            """)
    List<Events> findEventsByDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );
}
