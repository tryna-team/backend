package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.mapping.UserEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEventsRepository extends JpaRepository<UserEvents, Long> {

    /**
     * 특정 사용자가 특정 일정에 연결되어 있는지 확인합니다.
     *
     * E105, E106, F103에서 현재 사용자가 해당 일정에 접근할 수 있는지
     * 확인하기 위해 사용합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 일정 ID
     * @return 사용자와 일정의 연결 정보가 존재하면 true
     */
    boolean existsByUser_UserIdAndEvent_EventId(
            Long userId,
            Long eventId
    );

    /**
     * 회원 탈퇴 시 해당 사용자의 일정 연결 정보를 모두 삭제합니다.
     *
     * @param userId 탈퇴하는 사용자 ID
     * @return 삭제된 행 개수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserEvents ue
             WHERE ue.user.userId = :userId
            """)
    int deleteByUserId(
            @Param("userId") Long userId
    );
}