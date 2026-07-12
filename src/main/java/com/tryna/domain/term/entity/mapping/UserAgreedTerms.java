package com.tryna.domain.term.entity.mapping;

import com.tryna.domain.term.entity.Terms;
import com.tryna.domain.user.entity.Users;
import com.tryna.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(
        name = "user_agreed_terms",
        indexes = {
                @Index(name = "idx_user_agreed_terms_user_id", columnList = "user_id"),
                @Index(name = "idx_user_agreed_terms_term_id", columnList = "term_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreedTerms extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_agreed_term_id")
    private Long userAgreedTermId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Terms term;

    // A105 소셜 회원가입 시 약관 매핑 객체를 쉽게 생성하기 위한 메서드
    public static UserAgreedTerms create(Users user, Terms term) {
        UserAgreedTerms agreedTerms = new UserAgreedTerms();
        agreedTerms.user = user;
        agreedTerms.term = term;
        return agreedTerms;
    }
}
