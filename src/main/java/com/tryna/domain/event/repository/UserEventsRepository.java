package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.mapping.UserEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEventsRepository extends JpaRepository<UserEvents, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserEvents ue
             WHERE ue.user.userId = :userId
            """)
    int deleteByUserId(@Param("userId") Long userId);
}