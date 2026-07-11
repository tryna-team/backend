package com.tryna.domain.action.repository;

import com.tryna.domain.action.entity.ActionItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

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
