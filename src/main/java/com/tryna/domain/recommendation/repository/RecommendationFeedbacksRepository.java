package com.tryna.domain.recommendation.repository;

import com.tryna.domain.recommendation.entity.mapping.RecommendationFeedbacks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationFeedbacksRepository
        extends JpaRepository<RecommendationFeedbacks, Long> {

    /**
     * 회원 탈퇴 시 해당 사용자의 추천 피드백 로그를 모두 삭제합니다.
     *
     * @param userId 탈퇴하는 사용자 ID
     * @return 삭제된 행 개수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM RecommendationFeedbacks r
             WHERE r.user.userId = :userId
            """)
    int deleteByUserId(
            @Param("userId") Long userId
    );
}