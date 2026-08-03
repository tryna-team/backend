package com.tryna.domain.label.repository;

import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabelsRepository extends JpaRepository<Labels, Long> {

    List<Labels> findAllByUser_UserIdOrderBySortOrderAsc(
            Long userId
    );

    Optional<Labels> findByLabelIdAndUser_UserId(
            Long labelId,
            Long userId
    );

    Optional<Labels> findByUser_UserIdAndIsDefaultTrue(
            Long userId
    );

    boolean existsByUser_UserIdAndNormalizedName(
            Long userId,
            String normalizedName
    );

    boolean existsByUser_UserIdAndNormalizedNameAndLabelIdNot(
            Long userId,
            String normalizedName,
            Long labelId
    );

    Optional<Labels> findTopByUser_UserIdOrderBySortOrderDesc(
            Long userId
    );

    long countByUser_UserId(
            Long userId
    );

    List<Labels> findAllByUser_UserIdAndSortOrderGreaterThanEqualOrderBySortOrderAsc(
            Long userId,
            Integer sortOrder
    );

    boolean existsByUser_UserIdAndLabelType(
            Long userId,
            LabelType labelType
    );

    List<Labels> findAllByUser_UserIdAndLabelTypeOrderBySortOrderAsc(
            Long userId,
            LabelType labelType
    );
}