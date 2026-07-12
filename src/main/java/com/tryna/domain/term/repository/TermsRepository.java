package com.tryna.domain.term.repository;

import com.tryna.domain.term.entity.Terms;
import com.tryna.domain.term.enums.TermType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TermsRepository extends JpaRepository<Terms, Long> {
    // 입력받은 약관 유형(TermType)에 대해 각각 '최신 버전(가장 큰 ID)'의 약관 엔티티들을 조회
    @Query("SELECT t FROM Terms t WHERE t.termType IN :termTypes AND t.termId IN " +
            "(SELECT MAX(t2.termId) FROM Terms t2 GROUP BY t2.termType)")
    List<Terms> findLatestTermsByTypes(@Param("termTypes") List<TermType> termTypes);

    @Query("SELECT t.termType FROM Terms t WHERE t.isRequired = true AND t.termId IN " +
            "(SELECT MAX(t2.termId) FROM Terms t2 GROUP BY t2.termType)")
    List<TermType> findRequiredTermTypes();
}