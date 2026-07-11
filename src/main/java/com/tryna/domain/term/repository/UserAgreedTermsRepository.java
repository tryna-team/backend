package com.tryna.domain.term.repository;

import com.tryna.domain.term.entity.mapping.UserAgreedTerms;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreedTermsRepository extends JpaRepository<UserAgreedTerms, Long> {
}