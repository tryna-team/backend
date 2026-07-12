package com.tryna.domain.action.repository;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ItemType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
}