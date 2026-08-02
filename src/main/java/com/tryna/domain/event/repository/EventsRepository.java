package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.external.entity.ExternalCalendars;
import com.tryna.domain.external.enums.ConnectionStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventsRepository extends JpaRepository<Events, Long> {

    Optional<Events> findByExternalCalendarAndExternalEventId(com.tryna.domain.external.entity.ExternalCalendars externalCalendar, String externalEventId);

    List<Events> findByExternalCalendarAndExternalEventIdIn(ExternalCalendars externalCalendar, Collection<String> externalEventIds);

    void deleteAllByExternalCalendar(ExternalCalendars externalCalendar);

    @Query("""
            SELECT COUNT(e) > 0
              FROM Events e
             WHERE e.eventId = :eventId
               AND e.eventStatus IN :eventStatuses
               AND e.deletedAt IS NULL
            """)
    boolean existsVisibleByEventIdAndEventStatusIn(
            @Param("eventId") Long eventId,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    @Query("""
            SELECT e
              FROM Events e
              LEFT JOIN e.externalCalendar ec
             LEFT JOIN ec.connection ecc
             WHERE e.eventId = :eventId
               AND e.eventStatus IN :eventStatuses
               AND e.deletedAt IS NULL
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

    // 단건 일정 삭제용 (Event 도메인에서 사용)
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

    // 회원 탈퇴 시 전체 일정 벌크 삭제용 (User 도메인 탈퇴 로직에서 사용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Events e
               SET e.deletedAt = :deletedAt
             WHERE e.eventId IN (
                   SELECT ue.event.eventId
                     FROM UserEvents ue
                    WHERE ue.user.userId = :userId
                      AND ue.eventRole = 'OWNER'
             )
               AND e.deletedAt IS NULL
            """)
    int softDeleteByUserId(
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
