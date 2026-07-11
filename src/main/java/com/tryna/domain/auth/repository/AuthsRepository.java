package com.tryna.domain.auth.repository;

import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthsRepository extends JpaRepository<Auths, Long> {
    // 기존 가입자인지 판별 (탈퇴한 계정 제외)
    Optional<Auths> findByProviderAndSocialIdAndDeletedAtIsNull(Provider provider, String socialId);

    // G103: 활성 상태인 소셜 연동 정보만 조회
    List<Auths> findAllByUser_UserIdAndDeletedAtIsNull(Long userId);

    // G104: Soft Delete 벌크 연산
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Auths a
               SET a.deletedAt = :deletedAt
             WHERE a.user.userId = :userId
               AND a.deletedAt IS NULL
            """)
    int softDeleteByUserId(
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}