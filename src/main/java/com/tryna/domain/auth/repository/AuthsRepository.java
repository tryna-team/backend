package com.tryna.domain.auth.repository;

import com.tryna.domain.auth.entity.Auths;
import com.tryna.domain.auth.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthsRepository extends JpaRepository<Auths, Long> {
    // 기존 가입자인지 판별 (탈퇴한 계정 제외)
    Optional<Auths> findByProviderAndSocialIdAndDeletedAtIsNull(Provider provider, String socialId);
}