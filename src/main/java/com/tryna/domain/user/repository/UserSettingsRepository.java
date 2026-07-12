package com.tryna.domain.user.repository;

import com.tryna.domain.user.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    // 회원 탈퇴 시 유저 설정 정보 삭제용 (User 도메인 탈퇴 로직에서 사용)
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