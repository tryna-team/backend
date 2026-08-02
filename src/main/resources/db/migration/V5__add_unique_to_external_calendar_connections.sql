-- V5: external_calendar_connections 중복 제거 전 종속 캘린더 및 이벤트 데이터 정합성(이관) 보장

-- 1. 중복 커넥션 중 구형('a')에 속한 캘린더의 일정(events)들을
--    신형('b')의 동일한 캘린더(provider_external_calendar_id)로 이관하여 역사적 데이터 유실 방지
UPDATE events e
SET external_calendar_id = target_cal.external_calendar_id
    FROM external_calendars source_cal
JOIN external_calendars target_cal ON target_cal.provider_external_calendar_id = source_cal.provider_external_calendar_id
    JOIN external_calendar_connections b ON target_cal.external_calendar_connection_id = b.external_calendar_connection_id
    JOIN external_calendar_connections a ON source_cal.external_calendar_connection_id = a.external_calendar_connection_id
WHERE e.external_calendar_id = source_cal.external_calendar_id
  AND a.external_calendar_connection_id < b.external_calendar_connection_id
  AND a.user_id = b.user_id
  AND a.provider = b.provider;

-- 2. 구형 커넥션('a')에 종속되어 있던 캘린더 레코드 안전 삭제 (이벤트가 'b'쪽으로 모두 이관되었으므로 충돌 없음)
DELETE FROM external_calendars source_cal
    USING external_calendar_connections a, external_calendar_connections b
WHERE source_cal.external_calendar_connection_id = a.external_calendar_connection_id
  AND a.external_calendar_connection_id < b.external_calendar_connection_id
  AND a.user_id = b.user_id
  AND a.provider = b.provider;

-- 3. 이제 하위 종속 데이터가 모두 안전하게 정리된 중복 커넥션 'a' 삭제
DELETE FROM external_calendar_connections a
    USING external_calendar_connections b
WHERE a.external_calendar_connection_id < b.external_calendar_connection_id
  AND a.user_id = b.user_id
  AND a.provider = b.provider;

-- 4. 유니크 제약 조건 추가
ALTER TABLE external_calendar_connections
    DROP CONSTRAINT IF EXISTS uq_external_calendar_connections_user_provider;

ALTER TABLE external_calendar_connections
    ADD CONSTRAINT uq_external_calendar_connections_user_provider
        UNIQUE (user_id, provider);