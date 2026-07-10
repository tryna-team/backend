package com.tryna.domain.recommendation.repository;

import com.tryna.domain.recommendation.entity.mapping.RecommendationFeedbacks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationFeedbacksRepository
        extends JpaRepository<RecommendationFeedbacks, Long> {
}