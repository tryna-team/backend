package com.tryna.domain.user.entity;

import com.tryna.domain.user.enums.UserRole;
import com.tryna.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_guest_id", columnNames = "guest_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 20)
    private UserRole userRole;

    @Column(name = "guest_id", length = 255)
    private String guestId;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserSettings userSettings;

    // 비회원 생성용 정적 팩토리 메서드
    public static Users createGuest(String guestId) {
        Users user = new Users();
        user.userRole = UserRole.GUEST;
        user.guestId = guestId;
        return user;
    }

    // 정식 회원(소셜 로그인/가입) 생성용 정적 팩토리 메서드
    public static Users createUser() {
        Users user = new Users();
        user.userRole = UserRole.USER;
        return user;
    }

    // A106 회원 전환용 승급 메서드
    public void upgradeToUser() {
        this.userRole = UserRole.USER;
        this.guestId = null; // 다른 사람이 해당 기기로 다시 비회원을 시작할 수 있도록 비워줌
    }

    // 회원 탈퇴 시 계정 Soft Delete 처리용 메서드
    public void deleteSoft() {
        this.deletedAt = LocalDateTime.now();
    }
}
