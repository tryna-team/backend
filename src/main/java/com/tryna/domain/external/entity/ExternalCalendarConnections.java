package com.tryna.domain.external.entity;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.external.enums.ConnectionStatus;
import com.tryna.domain.user.entity.Users;
import com.tryna.global.converter.StringCryptoConverter;
import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "external_calendar_connections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_external_calendar_connections_user_provider",
                        columnNames = {"user_id", "provider"}
                )
        },
        indexes = {
                @Index(name = "idx_external_calendar_connections_user_id", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalCalendarConnections extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_calendar_connection_id")
    private Long externalCalendarConnectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private Provider provider;

    @Convert(converter = StringCryptoConverter.class)
    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 50)
    @ColumnDefault("'ACTIVE'")
    private ConnectionStatus connectionStatus = ConnectionStatus.ACTIVE;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_sync_status", length = 50)
    private String lastSyncStatus;

    public static ExternalCalendarConnections create(Users user, Provider provider, String refreshToken) {
        ExternalCalendarConnections connection = new ExternalCalendarConnections();
        connection.user = user;
        connection.provider = provider;
        connection.refreshToken = refreshToken;
        connection.connectionStatus = ConnectionStatus.ACTIVE;
        return connection;
    }

    public void updateSyncStatus(LocalDateTime at, String status) {
        this.lastSyncedAt = at;
        this.lastSyncStatus = status;
    }
}
