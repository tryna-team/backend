package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.RecurringEventExceptions;
import com.tryna.domain.event.enums.RecurringEventExceptionType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringEventExceptionsRepository extends JpaRepository<RecurringEventExceptions, Long> {

    boolean existsByEvent_EventIdAndOccurrenceDateAndExceptionType(
            Long eventId,
            LocalDate occurrenceDate,
            RecurringEventExceptionType exceptionType
    );

    @Query("""
            SELECT r.event.eventId, r.occurrenceDate
              FROM RecurringEventExceptions r
             WHERE r.event.eventId IN :eventIds
               AND r.occurrenceDate BETWEEN :startDate AND :endDate
               AND r.exceptionType = :exceptionType
            """)
    List<Object[]> findExceptionKeysInRange(
            @Param("eventIds") Collection<Long> eventIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("exceptionType") RecurringEventExceptionType exceptionType
    );
}
