package com.tryna.domain.label.repository;

import com.tryna.domain.label.entity.Labels;
import com.tryna.domain.label.enums.LabelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tryna.domain.external.entity.ExternalCalendars;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LabelsRepository extends JpaRepository<Labels, Long> {

    Optional<Labels> findByExternalCalendarAndDeletedAtIsNull(ExternalCalendars externalCalendar);

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

    List<Labels> findAllByUser_UserIdOrderBySortOrderAscLabelIdAsc(
            Long userId
    );

    List<Labels> findAllByUser_UserIdAndLabelTypeOrderBySortOrderAsc(
            Long userId,
            LabelType labelType
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Labels l SET l.deletedAt = :now WHERE l.user.userId = :userId AND l.deletedAt IS NULL")
    void softDeleteByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Labels l WHERE l.externalCalendar = :externalCalendar")
    int deleteByExternalCalendar(@Param("externalCalendar") com.tryna.domain.external.entity.ExternalCalendars externalCalendar);
}