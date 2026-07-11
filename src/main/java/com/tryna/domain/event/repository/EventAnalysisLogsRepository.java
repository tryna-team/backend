package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.EventAnalysisLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventAnalysisLogsRepository extends JpaRepository<EventAnalysisLogs, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM EventAnalysisLogs eal
             WHERE eal.event.eventId IN (
                   SELECT ue.event.eventId 
                     FROM UserEvents ue 
                    WHERE ue.user.userId = :userId
             )
            """)
    int deleteByUserId(@Param("userId") Long userId);
}