package com.tryna.domain.event.entity.mapping;

import com.tryna.domain.event.entity.Events;
import com.tryna.domain.event.enums.EventRole;
import com.tryna.domain.label.entity.Labels;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
        name = "user_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_events_user_id_event_id", columnNames = {"user_id", "event_id"})
        },
        indexes = {
                @Index(name = "idx_user_events_user_id", columnList = "user_id"),
                @Index(name = "idx_user_events_event_id", columnList = "event_id"),
                @Index(
                        name = "uq_user_events_owner",
                        columnList = "event_id",
                        unique = true,
                        options = "WHERE event_role = 'OWNER'"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEvents extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_event_id")
    private Long userEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Events event;


    // Todo: nullable 추가
    // @JoinColumn(name = "label_id", nullable = false)를 임시로 변경했습니다.
    // C104 변경 완료 후 추가할 에정입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_id")
    private Labels label;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_role", nullable = false, length = 50)
    @ColumnDefault("'OWNER'")
    private EventRole eventRole = EventRole.OWNER;

    public static UserEvents createOwner(Users user, Events event) {
        UserEvents userEvent = new UserEvents();
        userEvent.user = user;
        userEvent.event = event;
        userEvent.eventRole = EventRole.OWNER;
        return userEvent;
    }

    /**
     * 라벨을 포함한 일정 소유자 매핑을 생성합니다.
     *
     * @param user 일정 소유 사용자
     * @param event 연결할 일정
     * @param label 일정에 지정할 라벨
     * @return 생성된 사용자-일정 매핑
     */
    public static UserEvents createOwner(
            Users user,
            Events event,
            Labels label
    ) {
        UserEvents userEvent = new UserEvents();
        userEvent.user = user;
        userEvent.event = event;
        userEvent.label = label;
        userEvent.eventRole = EventRole.OWNER;
        return userEvent;
    }

    /**
     * 사용자 일정에 지정된 라벨을 변경합니다.
     * 일정 수정 또는 사용자 라벨 삭제 후 기본 라벨로 이동할 때 사용합니다.
     * @param label 새로 지정할 라벨
     */
    public void changeLabel(Labels label) {
        this.label = label;
    }

}
