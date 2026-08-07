package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.enums.EventStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.external.entity.ExternalCalendars;
import com.tryna.domain.label.entity.Labels;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEventsRepository extends JpaRepository<UserEvents, Long> {

    void deleteAllByEvent_ExternalCalendar(ExternalCalendars externalCalendar);

    @Query("""
            SELECT COUNT(ue.userEventId)
              FROM UserEvents ue
              JOIN ue.event e
             WHERE ue.user.userId = :userId
               AND e.eventStatus IN :eventStatuses
            """)
    long countVisibleEventsByUserId(
            @Param("userId") Long userId,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    @Query("""
            SELECT ue
              FROM UserEvents ue
              JOIN FETCH ue.event e
              LEFT JOIN FETCH ue.label
             WHERE ue.user.userId = :userId
               AND e.startDate IS NOT NULL
               AND e.startDate <= :endDate
               AND COALESCE(e.endDate, e.startDate) >= :startDate
               AND e.eventStatus IN :eventStatuses
               AND NOT EXISTS (
                    SELECT 1
                      FROM RecurringEventExceptions ree
                     WHERE ree.event = e
                       AND ree.occurrenceDate = e.startDate
                       AND ree.exceptionType = com.tryna.domain.event.enums.RecurringEventExceptionType.DELETED
               )
            """)
    List<UserEvents> findUserEventsOverlappingRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    @Query("""
            SELECT ue
             FROM UserEvents ue
             JOIN FETCH ue.event e
             LEFT JOIN FETCH ue.label
            WHERE ue.user.userId = :userId
              AND e.startDate IS NOT NULL
              AND e.startDate <= :date
              AND COALESCE(e.endDate, e.startDate) >= :date
              AND e.eventStatus IN :eventStatuses
              AND NOT EXISTS (
                   SELECT 1
                     FROM RecurringEventExceptions ree
                    WHERE ree.event = e
                       AND ree.occurrenceDate = e.startDate
                       AND ree.exceptionType = com.tryna.domain.event.enums.RecurringEventExceptionType.DELETED
               )
             ORDER BY
               CASE WHEN e.startDatetime IS NULL THEN 1 ELSE 0 END,
               e.startDatetime ASC,
               e.createdAt ASC
            """)
    List<UserEvents> findUserEventsByDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    @Query("""
            SELECT ue
              FROM UserEvents ue
              JOIN FETCH ue.event e
              LEFT JOIN FETCH ue.label
             WHERE ue.user.userId = :userId
               AND e.isRecurring = true
               AND e.startDate IS NOT NULL
               AND e.startDate <= :endDate
               AND (
                    e.recurrenceEndDate IS NULL
                    OR e.recurrenceEndDate >= :startDateTime
                    OR (
                        e.endDate IS NOT NULL
                        AND e.endDate > e.startDate
                    )
               )
               AND e.eventStatus IN :eventStatuses
            """)
    List<UserEvents> findRecurringUserEventsInRange(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDate") LocalDate endDate,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    /**
     * 특정 사용자가 특정 일정에 연결되어 있는지 확인합니다.
     *
     * C104, E106, F103에서 현재 사용자가 해당 일정에 접근할 수 있는지
     * 확인하기 위해 사용합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 일정 ID
     * @return 사용자와 일정의 연결 정보가 존재하면 true
     */
    boolean existsByUser_UserIdAndEvent_EventId(
            Long userId,
            Long eventId
    );

    @Query("""
            SELECT COUNT(ue.userEventId) > 0
              FROM UserEvents ue
             WHERE ue.user.userId = :userId
               AND ue.event.eventId = :eventId
               AND ue.eventRole = com.tryna.domain.event.enums.EventRole.OWNER
            """)
    boolean existsOwnerByUserIdAndEventId(
            @Param("userId") Long userId,
            @Param("eventId") Long eventId
    );

    @Query("""
            SELECT ue.label.labelId
              FROM UserEvents ue
             WHERE ue.user.userId = :userId
               AND ue.event.eventId = :eventId
            """)
    Optional<Long> findLabelIdByUserIdAndEventId(
            @Param("userId") Long userId,
            @Param("eventId") Long eventId
    );

    @Query("""
        SELECT ue.event.eventId,
               ue.label.labelId,
               ue.label.name,
               ue.label.color
          FROM UserEvents ue
         WHERE ue.user.userId = :userId
           AND ue.event.eventId IN :eventIds
        """)
    List<Object[]> findLabelIdsByUserIdAndEventIds(
            @Param("userId") Long userId,
            @Param("eventIds") Collection<Long> eventIds
    );

    /**
     * 회원 탈퇴 시 해당 사용자의 일정 연결 정보를 모두 삭제합니다.
     *
     * @param userId 탈퇴하는 사용자 ID
     * @return 삭제된 행 개수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserEvents ue
             WHERE ue.user.userId = :userId
            """)
    int deleteByUserId(
            @Param("userId") Long userId
    );

    /**
     * 현재 사용자의 Tryna 내부 일정 중 제목에 검색어가 포함된 일정을 조회합니다.
     *
     * 외부 캘린더 일정과 검색 결과에 노출하지 않는 상태의 일정은 제외합니다.
     *
     * @param userId 현재 사용자 ID
     * @param keyword 검색 키워드
     * @param eventStatuses 검색 가능한 일정 상태
     * @param excludedSourceType 검색에서 제외할 일정 출처 유형
     * @return 일정 제목이 검색어와 일치한 일정 목록
     */
    @Query("""
            SELECT e
              FROM UserEvents ue
              JOIN ue.event e
             WHERE ue.user.userId = :userId
               AND e.eventStatus IN :eventStatuses
               AND e.sourceType <> :excludedSourceType
               AND LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Events> findInternalEventsByTitleContaining(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses,
            @Param("excludedSourceType") SourceType excludedSourceType
    );

    /**
     * 삭제 대상 라벨에 연결된 사용자-일정 매핑을 기본 라벨로 일괄 변경합니다.
     *
     * B108-4 라벨 삭제 시 일정 자체는 삭제하지 않고,
     * 해당 사용자의 기본 라벨로 이동하기 위해 사용합니다.
     *
     * @param userId 현재 사용자 ID
     * @param sourceLabelId 삭제 대상 라벨 ID
     * @param destinationLabel 이동할 기본 라벨
     * @return 라벨이 변경된 사용자-일정 매핑 개수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE UserEvents ue
           SET ue.label = :destinationLabel
         WHERE ue.user.userId = :userId
           AND ue.label.labelId = :sourceLabelId
        """)
    int moveLabelAssignments(
            @Param("userId") Long userId,
            @Param("sourceLabelId") Long sourceLabelId,
            @Param("destinationLabel") Labels destinationLabel
    );

    @Query("""
            SELECT e, COUNT(e)
              FROM UserEvents ue
              JOIN ue.event e
             WHERE ue.user.userId = :userId
               AND e.startDate IS NOT NULL
               AND e.startDate <= :endDate
               AND COALESCE(e.endDate, e.startDate) >= :startDate
               AND e.eventStatus IN :eventStatuses
             GROUP BY e
            """)
    List<Object[]> countEventsByDate(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );
}
