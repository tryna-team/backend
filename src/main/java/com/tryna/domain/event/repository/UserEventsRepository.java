package com.tryna.domain.event.repository;

import com.tryna.domain.event.entity.mapping.UserEvents;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEventsRepository extends JpaRepository<UserEvents, Long> {

    /**
     * 특정 사용자가 특정 일정에 연결되어 있는지 확인합니다.
     *
     * E105에서 현재 사용자가 해당 일정에 준비/실행 항목을
     * 저장할 수 있는지 확인하기 위해 사용합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 일정 ID
     * @return 사용자와 일정의 연결 정보가 존재하면 true
     */
    boolean existsByUser_UserIdAndEvent_EventId(
            Long userId,
            Long eventId
    );
}