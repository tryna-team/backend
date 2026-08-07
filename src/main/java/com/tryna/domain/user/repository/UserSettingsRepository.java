package com.tryna.domain.user.repository;

import com.tryna.domain.user.entity.UserSettings;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserSettings u
               SET u.deletedAt = :deletedAt
             WHERE u.user.userId = :userId
               AND u.deletedAt IS NULL
            """)
    int softDeleteByUserId(
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
