package com.tryna.domain.auth.entity;

import com.tryna.domain.auth.enums.Provider;
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

import java.time.LocalDateTime;

@Entity
@Table(
        name = "auths",
        indexes = {
                @Index(name = "idx_auths_user_id", columnList = "user_id"),
                @Index(
                        name = "uq_auths_provider_social_id_active",
                        columnList = "provider, social_id",
                        unique = true,
                        options = "WHERE deleted_at IS NULL"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auths extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_id")
    private Long authId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private Provider provider;

    @Column(name = "social_id", nullable = false, length = 255)
    private String socialId;

    @Column(name = "email", length = 255)
    private String email;

    // 명세서 삭제 정책: Soft Delete
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Auths createAuth(Users user, Provider provider, String socialId, String email) {
        Auths auth = new Auths();
        auth.user = user;
        auth.provider = provider;
        auth.socialId = socialId;
        auth.email = email;
        return auth;
    }
}
