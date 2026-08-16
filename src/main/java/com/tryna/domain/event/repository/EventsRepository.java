package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.external.entity.ExternalCalendars;
import com.tryna.domain.external.enums.ConnectionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventsRepository extends JpaRepository<Events, Long> {

    @Query("""
            SELECT COUNT(e) > 0
              FROM Events e
             WHERE e.externalCalendar = :externalCalendar
               AND e.startDate IS NOT NULL
               AND e.startDate <= :endDate
               AND COALESCE(e.endDate, e.startDate) >= :startDate
               AND e.eventStatus IN :eventStatuses
               AND e.deletedAt IS NULL
            """)
    boolean existsByExternalCalendarAndDateRange(
            @Param("externalCalendar") ExternalCalendars externalCalendar,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

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
                 , e.eventStatus = com.tryna.domain.event.enums.EventStatus.DELETED
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
                 , e.eventStatus = :deletedStatus
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
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("deletedStatus") EventStatus deletedStatus
    );

    // 외부 캘린더 연동 해제 시 소속된 일정들을 Soft Delete
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Events e
               SET e.deletedAt = :deletedAt
                 , e.eventStatus = com.tryna.domain.event.enums.EventStatus.DELETED
             WHERE e.externalCalendar = :externalCalendar
               AND e.deletedAt IS NULL
            """)
    int softDeleteByExternalCalendar(
            @Param("externalCalendar") ExternalCalendars externalCalendar,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    // 유저 ID와 외부 이벤트 ID 목록으로 삭제된(DELETED) 기존 일정까지 포함하여 한 번에 조회 (Native Query)
    @Query(value = """
            SELECT e.* 
              FROM events e
              JOIN user_events ue ON ue.event_id = e.event_id
             WHERE ue.user_id = :userId
               AND e.external_event_id IN (:externalEventIds)
               AND e.source_type = 'EXTERNAL_CALENDAR'
            """, nativeQuery = true)
    List<Events> findAllIncludingDeletedByUserIdAndExternalEventIdIn(
            @Param("userId") Long userId,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    /**
     * 특정 기간 내의 시스템 공휴일 일정만 단독으로 조회합니다.
     */
    @Query("""
            SELECT e
              FROM Events e
             WHERE e.sourceType = com.tryna.domain.event.enums.SourceType.HOLIDAY
               AND e.startDate IS NOT NULL
               AND e.startDate <= :endDate
               AND COALESCE(e.endDate, e.startDate) >= :startDate
               AND e.deletedAt IS NULL
            """)
    List<Events> findHolidaysInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 공휴일 중복 삽입 방지를 위해 존재 여부를 확인합니다.
     * (Native Query를 사용하여 @SQLRestriction을 무시하고 Soft Delete 된 일정까지 완벽히 검사합니다)
     */
    @Query(value = """
            SELECT COUNT(*) > 0
              FROM events
             WHERE source_type = 'HOLIDAY'
               AND external_event_id = :externalEventId
            """, nativeQuery = true)
    boolean existsHolidayByExternalEventId(@Param("externalEventId") String externalEventId);
}
