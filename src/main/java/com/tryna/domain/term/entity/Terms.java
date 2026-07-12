package com.tryna.domain.term.entity;

import com.tryna.domain.term.enums.TermType;
import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "terms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_terms_type_version", columnNames = {"term_type", "version"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "term_id")
    private Long termId;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 50)
    private TermType termType;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

}
