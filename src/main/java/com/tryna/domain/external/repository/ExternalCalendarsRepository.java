package com.tryna.domain.external.repository;

import com.tryna.domain.auth.enums.Provider;
import com.tryna.domain.external.entity.ExternalCalendarConnections;
import com.tryna.domain.external.entity.ExternalCalendars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExternalCalendarsRepository extends JpaRepository<ExternalCalendars, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ExternalCalendars ec
             WHERE ec.connection.externalCalendarConnectionId IN (
                   SELECT ecc.externalCalendarConnectionId 
                     FROM ExternalCalendarConnections ecc 
                    WHERE ecc.user.userId = :userId
             )
            """)
    int deleteByUserId(@Param("userId") Long userId);

    Optional<ExternalCalendars> findByConnection_User_UserIdAndConnection_ProviderAndProviderExternalCalendarId(
            Long userId, Provider provider, String providerExternalCalendarId
    );

    List<ExternalCalendars> findAllByConnection(ExternalCalendarConnections connection);

}