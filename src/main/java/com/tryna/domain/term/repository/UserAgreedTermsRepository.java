package com.tryna.domain.term.repository;

import com.tryna.domain.term.entity.mapping.UserAgreedTerms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAgreedTermsRepository extends JpaRepository<UserAgreedTerms, Long> {
    boolean existsByUser_UserIdAndTerm_TermId(Long userId, Long termId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserAgreedTerms u
             WHERE u.user.userId = :userId
            """)
    int deleteByUserId(@Param("userId") Long userId);
}