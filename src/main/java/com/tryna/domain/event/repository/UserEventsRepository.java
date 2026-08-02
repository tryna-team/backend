package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.entity.mapping.UserEvents;
import com.tryna.domain.event.enums.EventStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.tryna.domain.event.enums.SourceType;
import com.tryna.domain.external.entity.ExternalCalendars;
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

    @Query("""
            SELECT e
              FROM UserEvents ue
              JOIN ue.event e
             WHERE ue.user.userId = :userId
               AND e.isRecurring = true
               AND e.startDate IS NOT NULL
               AND e.startDate <= :endDate
               AND (
                    e.recurrenceEndDate IS NULL
                    OR e.recurrenceEndDate >= :startDateTime
               )
               AND e.eventStatus IN :eventStatuses
            """)
    List<Events> findRecurringEventsInRange(
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
}
