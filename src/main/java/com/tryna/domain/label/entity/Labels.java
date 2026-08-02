package com.tryna.domain.label.entity;

import com.tryna.domain.external.entity.ExternalCalendars;
import com.tryna.domain.label.enums.LabelColor;
import com.tryna.domain.label.enums.LabelType;
import com.tryna.domain.user.entity.Users;
import com.tryna.global.entity.BaseEntity;
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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "labels",
        indexes = {
                @Index(
                        name = "idx_labels_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_labels_external_calendar_id",
                        columnList = "external_calendar_id"
                ),
                @Index(
                        name = "idx_labels_user_sort_order",
                        columnList = "user_id, sort_order"
                ),
                @Index(
                        name = "uq_labels_user_normalized_name_active",
                        columnList = "user_id, normalized_name",
                        unique = true,
                        options = "WHERE deleted_at IS NULL"
                ),
                @Index(
                        name = "uq_labels_user_default_active",
                        columnList = "user_id",
                        unique = true,
                        options = "WHERE is_default = true AND deleted_at IS NULL"
                ),
                @Index(
                        name = "uq_labels_external_calendar_active",
                        columnList = "external_calendar_id",
                        unique = true,
                        options = "WHERE external_calendar_id IS NOT NULL AND deleted_at IS NULL"
                )
        }
)
@Check(
        constraints = """
                (
                    label_type IN ('DEFAULT', 'USER')
                    AND external_calendar_id IS NULL
                )
                OR
                (
                    label_type = 'EXTERNAL_CALENDAR'
                    AND external_calendar_id IS NOT NULL
                    AND is_default = false
                )
                """
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Labels extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "label_id")
    private Long labelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "external_calendar_id")
    private ExternalCalendars externalCalendar;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "label_type", nullable = false, length = 30)
    @ColumnDefault("'USER'")
    private LabelType labelType = LabelType.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "color", nullable = false, length = 20)
    private LabelColor color;

    @Column(name = "is_default", nullable = false)
    @ColumnDefault("false")
    private Boolean isDefault = false;

    @Column(name = "is_visible", nullable = false)
    @ColumnDefault("true")
    private Boolean isVisible = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Labels createDefault(
            Users user,
            String name,
            String normalizedName,
            LabelColor color,
            Integer sortOrder
    ) {
        Labels label = new Labels();

        label.user = user;
        label.externalCalendar = null;
        label.name = name;
        label.normalizedName = normalizedName;
        label.labelType = LabelType.DEFAULT;
        label.color = color;
        label.isDefault = true;
        label.isVisible = true;
        label.sortOrder = sortOrder;

        return label;
    }

    public static Labels createUserLabel(
            Users user,
            String name,
            String normalizedName,
            LabelColor color,
            Integer sortOrder
    ) {
        Labels label = new Labels();

        label.user = user;
        label.externalCalendar = null;
        label.name = name;
        label.normalizedName = normalizedName;
        label.labelType = LabelType.USER;
        label.color = color;
        label.isDefault = false;
        label.isVisible = true;
        label.sortOrder = sortOrder;

        return label;
    }

    public static Labels createExternalCalendarLabel(
            Users user,
            ExternalCalendars externalCalendar,
            String name,
            String normalizedName,
            LabelColor color,
            Integer sortOrder
    ) {
        Labels label = new Labels();

        label.user = user;
        label.externalCalendar = externalCalendar;
        label.name = name;
        label.normalizedName = normalizedName;
        label.labelType = LabelType.EXTERNAL_CALENDAR;
        label.color = color;
        label.isDefault = false;
        label.isVisible = true;
        label.sortOrder = sortOrder;

        return label;
    }

    public void update(
            String name,
            String normalizedName,
            LabelColor color,
            Boolean isVisible
    ) {
        if (name != null) {
            this.name = name;
            this.normalizedName = normalizedName;
        }

        if (color != null) {
            this.color = color;
        }

        if (isVisible != null) {
            this.isVisible = isVisible;
        }
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}