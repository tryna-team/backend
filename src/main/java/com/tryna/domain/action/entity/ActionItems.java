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

    @Column(name = "occurrence_date")
    private LocalDate occurrenceDate;

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

    /**
     * E105: 준비/실행 항목 생성
     *
     * 프론트엔드에서 최종 확정된 준비/실행 항목 정보를 받아
     * 부모 일정과 연결된 ActionItems 엔티티를 생성합니다.
     */
    public static ActionItems create(
            Events parentEvent,
            String title,
            ItemType itemType,
            LocalDate occurrenceDate,
            LocalDate displayDate,
            LocalDateTime displayDatetime,
            Integer offsetDays,
            CreatedBy createdBy,
            String sourceTemplateId
    ) {
        ActionItems actionItem = new ActionItems();

        actionItem.parentEvent = parentEvent;
        actionItem.title = title;
        actionItem.itemType = itemType;
        actionItem.occurrenceDate = occurrenceDate;
        actionItem.displayDate = displayDate;
        actionItem.displayDatetime = displayDatetime;
        actionItem.offsetDays = offsetDays;
        actionItem.actionItemStatus = ActionItemStatus.PENDING;
        actionItem.createdBy = createdBy;
        actionItem.sourceTemplateId = sourceTemplateId;

        return actionItem;
    }

    /**
     * E106: 준비/실행 항목 상태 변경
     *
     * COMPLETED 상태로 변경하면 현재 시각을 완료 일시로 기록합니다.
     * PENDING 상태로 되돌리면 기존 완료 일시를 제거합니다.
     *
     * @param status 변경할 준비/실행 항목 상태
     */
    public void updateStatus(ActionItemStatus status) {
        this.actionItemStatus = status;

        if (status == ActionItemStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
            return;
        }

        this.completedAt = null;
    }

    public void restoreStatus(ActionItemStatus status, LocalDateTime completedAt) {
        this.actionItemStatus = status;
        this.completedAt = status == ActionItemStatus.COMPLETED ? completedAt : null;
    }

    public void updateDetails(
            String title,
            ItemType itemType,
            LocalDate occurrenceDate,
            LocalDate displayDate,
            LocalDateTime displayDatetime,
            Integer offsetDays,
            CreatedBy createdBy,
            String sourceTemplateId
    ) {
        this.title = title;
        this.itemType = itemType;
        this.occurrenceDate = occurrenceDate;
        this.displayDate = displayDate;
        this.displayDatetime = displayDatetime;
        this.offsetDays = offsetDays;
        this.createdBy = createdBy;
        this.sourceTemplateId = sourceTemplateId;
    }

    public boolean adjustDisplayDateByParentStartDate(LocalDate parentStartDate) {
        if (itemType != ItemType.TIMED_ACTION || offsetDays == null || parentStartDate == null) {
            return false;
        }

        LocalDate adjustedDate = parentStartDate.plusDays(offsetDays);
        this.displayDate = adjustedDate;

        if (displayDatetime != null) {
            this.displayDatetime = LocalDateTime.of(adjustedDate, displayDatetime.toLocalTime());
        }

        return true;
    }

    public boolean requiresReviewWhenParentDateMissing() {
        return itemType == ItemType.TIMED_ACTION && offsetDays != null;
    }

}
