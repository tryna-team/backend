package com.tryna.domain.event.entity.mapping;

import com.tryna.domain.event.enums.EventRole;
import com.tryna.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_role", nullable = false, length = 50)
    private EventRole eventRole = EventRole.OWNER;

}
