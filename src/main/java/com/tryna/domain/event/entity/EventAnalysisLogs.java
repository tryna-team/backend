package com.tryna.domain.event.entity;

import com.tryna.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(
        name = "event_analysis_logs",
        indexes = {
                @Index(name = "idx_event_analysis_logs_event_id", columnList = "event_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventAnalysisLogs extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_analysis_log_id")
    private Long eventAnalysisLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Events event;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "context_candidates", columnDefinition = "varchar[]")
    private List<String> contextCandidates;

    @Column(name = "place_type_candidate", length = 50)
    private String placeTypeCandidate;

    @Column(name = "condition_candidates", length = 255)
    private String conditionCandidates;

    @Column(name = "temporal_profile", length = 50)
    private String temporalProfile;

    @Column(name = "preparation_need_level", length = 20)
    private String preparationNeedLevel;

    @Column(name = "confidence_level", length = 20)
    private String confidenceLevel;

}
