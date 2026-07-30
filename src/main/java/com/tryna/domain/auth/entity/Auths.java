package com.tryna.domain.auth.entity;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.user.entity.Users;
import com.tryna.global.converter.StringCryptoConverter;
import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.*;
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

    @Convert(converter = StringCryptoConverter.class)
    @Column(name = "oauth_refresh_token", length = 512)
    private String oauthRefreshToken;

    @Column(name = "granted_scopes", length = 1000)
    private String grantedScopes;

    // 명세서 삭제 정책: Soft Delete
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Auths createAuth(Users user, Provider provider, String socialId, String email, String oauthRefreshToken, String grantedScopes) {        Auths auth = new Auths();
        auth.user = user;
        auth.provider = provider;
        auth.socialId = socialId;
        auth.email = email;
        auth.oauthRefreshToken = oauthRefreshToken;
        auth.grantedScopes = grantedScopes;
        return auth;
    }

    // 기존 회원 로그인 시 최신 토큰/권한으로 갱신하는 메서드 (더티 체킹)
    public void updateOAuthInfo(String oauthRefreshToken, String grantedScopes) {
        if (oauthRefreshToken != null && !oauthRefreshToken.isBlank()) {
            this.oauthRefreshToken = oauthRefreshToken;
        }
        if (grantedScopes != null && !grantedScopes.isBlank()) {
            this.grantedScopes = grantedScopes;
        }
    }
}
