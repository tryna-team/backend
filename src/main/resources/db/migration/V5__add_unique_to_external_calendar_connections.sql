-- V5: external_calendar_connections 테이블에 (user_id, provider) 유니크 제약 조건 추가
-- 만약 동시 요청 등으로 이미 중복 데이터가 존재할 경우를 대비해 최신 1개만 남기고 정리
DELETE FROM external_calendar_connections a
    USING external_calendar_connections b
WHERE a.external_calendar_connection_id < b.external_calendar_connection_id
  AND a.user_id = b.user_id
  AND a.provider = b.provider;

-- 유니크 제약 조건 추가
ALTER TABLE external_calendar_connections
    ADD CONSTRAINT uq_external_calendar_connections_user_provider
        UNIQUE (user_id, provider);