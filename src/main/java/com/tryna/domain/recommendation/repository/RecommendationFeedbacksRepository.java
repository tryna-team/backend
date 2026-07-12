package com.tryna.domain.recommendation.repository;

import com.tryna.domain.recommendation.entity.mapping.RecommendationFeedbacks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationFeedbacksRepository extends JpaRepository<RecommendationFeedbacks, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM RecommendationFeedbacks r
             WHERE r.user.userId = :userId
            """)
    int deleteByUserId(@Param("userId") Long userId);
}