package com.tryna.domain.action.entity;

import com.tryna.domain.action.enums.ActionItemStatus;
import com.tryna.domain.action.enums.CreatedBy;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.event.entity.Events;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "action_items",
        indexes = {
                @Index(name = "idx_action_items_parent_event_id", columnList = "parent_event_id"),
                @Index(name = "idx_action_items_display_date_time", columnList = "display_date, display_datetime")
        }
)
@Check(constraints = "(item_type = 'TIMED_ACTION' AND display_date IS NOT NULL) OR (item_type = 'UNTIMED_PREP' AND display_date IS NULL AND display_datetime IS NULL) OR (item_type = 'UNRESOLVED')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionItems extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_item_id")
    private Long actionItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_event_id", nullable = false)
    private Events parentEvent;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private ItemType itemType;

    @Column(name = "display_date")
    private LocalDate displayDate;

    @Column(name = "display_datetime")
    private LocalDateTime displayDatetime;

    @Column(name = "offset_days")
    private Integer offsetDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_item_status", nullable = false, length = 50)
    @ColumnDefault("'PENDING'")
    private ActionItemStatus actionItemStatus = ActionItemStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 50)
    private CreatedBy createdBy;

    @Column(name = "source_template_id", length = 100)
    private String sourceTemplateId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

}
