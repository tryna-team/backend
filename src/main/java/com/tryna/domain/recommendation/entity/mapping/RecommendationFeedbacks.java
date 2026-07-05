package com.tryna.domain.recommendation.entity.mapping;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.recommendation.enums.ActionType;
import com.tryna.domain.user.entity.Users;
import com.tryna.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "recommendation_feedbacks",
        indexes = {
                @Index(name = "idx_recommendation_feedbacks_user_id", columnList = "user_id"),
                @Index(name = "idx_recommendation_feedbacks_event_id", columnList = "event_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationFeedbacks extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_feedback_id")
    private Long recommendationFeedbackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Events event;

    @Column(name = "source_template_id", length = 100)
    private String sourceTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ActionType actionType;

    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(name = "edited_title", length = 255)
    private String editedTitle;

    @Column(name = "reason", length = 255)
    private String reason;

}
