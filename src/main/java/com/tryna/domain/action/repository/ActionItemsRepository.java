package com.tryna.domain.action.repository;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActionItemsRepository extends JpaRepository<ActionItems, Long> {

    // 단건 준비 항목(할 일) 삭제용 (ActionItem 도메인에서 사용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.actionItemId = :actionItemId
               AND a.deletedAt IS NULL
            """)
    int softDeleteById(
            @Param("actionItemId") Long actionItemId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventId(
            @Param("eventId") Long eventId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.actionItemId IN :actionItemIds
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventIdAndActionItemIdIn(
            @Param("eventId") Long eventId,
            @Param("actionItemIds") Collection<Long> actionItemIds,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.displayDate = :displayDate
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventIdAndDisplayDate(
            @Param("eventId") Long eventId,
            @Param("displayDate") LocalDate displayDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.displayDate >= :displayDate
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventIdFromDisplayDate(
            @Param("eventId") Long eventId,
            @Param("displayDate") LocalDate displayDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * 삭제되지 않은 준비/실행 항목을 ID로 조회합니다.
     *
     * @param actionItemId 준비/실행 항목 ID
     * @return 삭제되지 않은 준비/실행 항목
     */
    Optional<ActionItems> findByActionItemIdAndDeletedAtIsNull(
            Long actionItemId
    );

    /**
     * 특정 반복 일정에서 기준 회차 이후의 준비/실행 항목을 조회합니다.
     *
     * THIS_AND_FUTURE 수정 시 기준 회차 이후의 항목을
     * 새 반복 시리즈로 복사하기 위해 사용합니다.
     */
    List<ActionItems>
    findAllByParentEvent_EventIdAndOccurrenceDateGreaterThanEqualAndDeletedAtIsNullOrderByOccurrenceDateAscActionItemIdAsc(
            Long eventId,
            LocalDate occurrenceDate
    );

    /**
     * 특정 일정에 연결된 삭제되지 않은 준비/실행 항목을 조회합니다.
     *
     * 일정 상세 화면에서 일정한 순서로 표시할 수 있도록
     * 표시 날짜, 표시 일시, 항목 ID 순으로 정렬합니다.
     *
     * @param eventId 일정 ID
     * @return 일정에 연결된 준비/실행 항목 목록
     */
    @EntityGraph(attributePaths = "parentEvent")
    List<ActionItems>
    findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
            Long eventId
    );

    /**
     * 특정 일정에 연결된 삭제되지 않은 준비/실행 항목을 조회합니다.
     *
     * 일정 상세 화면에서 일정한 순서로 표시할 수 있도록
     * 표시 날짜, 표시 일시, 항목 ID 순으로 정렬합니다.
     *
     * @param eventId 일정 ID
     * @param occurrenceDate 반복 회차 소속 날짜
     * @return 일정에 연결된 준비/실행 항목 목록
     */
    @EntityGraph(attributePaths = "parentEvent")
    List<ActionItems>
    findAllByParentEvent_EventIdAndOccurrenceDateAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
            Long eventId,
            LocalDate occurrenceDate
    );

    List<ActionItems> findAllByParentEvent_EventIdAndDeletedAtIsNull(
            Long eventId
    );

    /**
     * 현재 사용자의 일정에 연결된 항목 중
     * 선택한 날짜에 표시할 시간형 실행 항목을 조회합니다.
     *
     * @param userId 현재 사용자 ID
     * @param date 조회 날짜
     * @param itemType 항목 유형
     * @return 시간형 실행 항목 목록
     */
    @Query("""
            SELECT a
              FROM ActionItems a
              JOIN FETCH a.parentEvent e
              JOIN UserEvents ue ON ue.event = e
             WHERE ue.user.userId = :userId
               AND a.displayDate = :date
               AND a.itemType = :itemType
               AND a.deletedAt IS NULL
             ORDER BY a.displayDatetime ASC, a.actionItemId ASC
            """)
    List<ActionItems> findCalendarActionItemsByDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("itemType") ItemType itemType
    );

    // 회원 탈퇴 시 전체 준비 항목 벌크 삭제용 (User 도메인 탈퇴 로직에서 사용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId IN (
                   SELECT ue.event.eventId
                     FROM UserEvents ue
                    WHERE ue.user.userId = :userId
                      AND ue.eventRole = 'OWNER'
             )
               AND a.deletedAt IS NULL
            """)
    int softDeleteByUserId(
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * 현재 사용자의 Tryna 내부 일정에 연결된 준비/실행 항목 중
     * 제목에 검색어가 포함된 항목을 조회합니다.
     *
     * 삭제된 항목, 외부 캘린더 일정에 연결된 항목,
     * 검색 결과에 노출하지 않는 상태의 일정에 연결된 항목은 제외합니다.
     *
     * @param userId 현재 사용자 ID
     * @param keyword 검색 키워드
     * @param eventStatuses 검색 가능한 일정 상태
     * @return 제목이 검색어와 일치한 준비/실행 항목 목록
     */
    @Query("""
            SELECT a
              FROM ActionItems a
              JOIN FETCH a.parentEvent e
              JOIN UserEvents ue ON ue.event = e
             WHERE ue.user.userId = :userId
               AND e.eventStatus IN :eventStatuses
               AND a.deletedAt IS NULL
               AND LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
             ORDER BY a.actionItemId ASC
            """)
    List<ActionItems> findSearchMatchesByUserIdAndKeyword(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    /**
     * 반복 일정의 특정 회차에 속한 준비/실행 항목을 soft delete 합니다.
     *
     * SINGLE 일정 수정 후 원본 회차의 항목이
     * 중복 노출되지 않도록 처리하기 위해 사용합니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ActionItems a
           SET a.deletedAt = :deletedAt
         WHERE a.parentEvent.eventId = :eventId
           AND a.occurrenceDate = :occurrenceDate
           AND a.deletedAt IS NULL
        """)
    int softDeleteByParentEventIdAndOccurrenceDate(
            @Param("eventId") Long eventId,
            @Param("occurrenceDate") LocalDate occurrenceDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * 반복 일정의 기준 회차 이후 준비/실행 항목을 모두 soft delete 합니다.
     *
     * THIS_AND_FUTURE 일정 수정 후 기존 시리즈의 이후 회차 항목이
     * 새 시리즈의 복사 항목과 중복되지 않도록 처리합니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ActionItems a
           SET a.deletedAt = :deletedAt
         WHERE a.parentEvent.eventId = :eventId
           AND a.occurrenceDate >= :occurrenceDate
           AND a.deletedAt IS NULL
        """)
    int softDeleteByParentEventIdFromOccurrenceDate(
            @Param("eventId") Long eventId,
            @Param("occurrenceDate") LocalDate occurrenceDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
