package com.tryna.domain.external.entity;

import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "external_calendars",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_external_calendars_connection_calendar",
                        columnNames = {"external_calendar_connection_id", "provider_external_calendar_id"}
                )
        },
        indexes = {
                @Index(name = "idx_external_calendars_connection_id", columnList = "external_calendar_connection_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalCalendars extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_calendar_id")
    private Long externalCalendarId;

    @Column(name = "external_calendar_connection_id", nullable = false)
    private Long externalCalendarConnectionId;

    @Column(name = "provider_external_calendar_id", nullable = false, length = 255)
    private String providerExternalCalendarId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "is_selected", nullable = false)
    private Boolean isSelected = true;

}
