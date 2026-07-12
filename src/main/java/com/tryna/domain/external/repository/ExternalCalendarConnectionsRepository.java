package com.tryna.domain.external.repository;

import com.tryna.domain.external.entity.ExternalCalendarConnections;
import com.tryna.domain.external.enums.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalCalendarConnectionsRepository extends JpaRepository<ExternalCalendarConnections, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ExternalCalendarConnections e
             WHERE e.user.userId = :userId
            """)
    int deleteByUserId(@Param("userId") Long userId);

    boolean existsByUser_UserIdAndConnectionStatus(Long userId, ConnectionStatus connectionStatus);
}