package com.tryna.domain.action.repository;

import com.tryna.domain.action.entity.ActionItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ActionItemsRepository extends JpaRepository<ActionItems, Long> {

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
}
