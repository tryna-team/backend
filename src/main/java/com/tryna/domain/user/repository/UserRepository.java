package com.tryna.domain.user.repository;

import com.tryna.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<Users, Long> {

    // A102: 기존 비회원 여부 확인 (Soft Delete 정책 반영)
    Optional<Users> findByGuestIdAndDeletedAtIsNull(String guestId);

    // A101: 삭제되지 않은 실제 일정이 존재하는지 확인 (Soft Delete 정책 반영)
    @Query(value = "SELECT EXISTS(" +
            "SELECT 1 FROM user_events ue " +
            "JOIN events e ON ue.event_id = e.event_id " +
            "WHERE ue.user_id = :userId AND e.deleted_at IS NULL)",
            nativeQuery = true)
    boolean hasActiveEvents(@Param("userId") Long userId);

    // A101: 외부 캘린더 연동 여부 확인 (ACTIVE 상태인 연결만 유효한 것으로 판단)
    @Query(value = "SELECT EXISTS(" +
            "SELECT 1 FROM external_calendar_connections " +
            "WHERE user_id = :userId AND connection_status = 'ACTIVE')",
            nativeQuery = true)
    boolean hasExternalCalendarConnection(@Param("userId") Long userId);
}
