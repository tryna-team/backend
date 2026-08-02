-- V5: 결정론적(Deterministic) 방식의 중복 커넥션 정리 및 데이터 정합성 보장 마이그레이션

DO $$
    DECLARE
    r RECORD;
cal_rec RECORD;
target_cal_id BIGINT;
BEGIN
    -- 1. 유저 및 프로바이더별로 중복이 존재하는 경우에만 순회 (가장 ID가 큰 최신 커넥션을 생존 대상으로 지정)
    FOR r IN
SELECT user_id, provider, MAX(external_calendar_connection_id) AS target_conn_id
FROM external_calendar_connections
GROUP BY user_id, provider
HAVING COUNT(*) > 1
    LOOP
        -- 2. 생존 커넥션(target_conn_id)이 아닌 구형(중복) 커넥션들에 속한 캘린더들을 순회
        FOR cal_rec IN
SELECT ec.external_calendar_id, ec.provider_external_calendar_id
FROM external_calendars ec
         JOIN external_calendar_connections ecc ON ec.external_calendar_connection_id = ecc.external_calendar_connection_id
WHERE ecc.user_id = r.user_id
  AND ecc.provider = r.provider
  AND ecc.external_calendar_connection_id <> r.target_conn_id
    LOOP
-- 3. 생존 커넥션 아래에 동일한 external_calendar_id를 가진 캘린더가 이미 존재하는지 확인
SELECT external_calendar_id INTO target_cal_id
FROM external_calendars
WHERE external_calendar_connection_id = r.target_conn_id
  AND provider_external_calendar_id = cal_rec.provider_external_calendar_id;

IF target_cal_id IS NOT NULL THEN
                -- [케이스 A] 이미 생존 커넥션쪽에 동일 캘린더가 존재함 -> 이벤트 중복 충돌 방지를 위해 구형 캘린더의 이벤트 중 겹치는 것 먼저 정리
DELETE FROM events src_e
    USING events tgt_e
WHERE src_e.external_calendar_id = cal_rec.external_calendar_id
  AND tgt_e.external_calendar_id = target_cal_id
  AND src_e.external_event_id = tgt_e.external_event_id;

-- 남은 이벤트들을 생존 캘린더 쪽으로 안전하게 이관
UPDATE events
SET external_calendar_id = target_cal_id
WHERE external_calendar_id = cal_rec.external_calendar_id;

-- 이벤트가 모두 비워진 구형 캘린더 삭제
DELETE FROM external_calendars
WHERE external_calendar_id = cal_rec.external_calendar_id;
ELSE
                -- [케이스 B] 생존 커넥션쪽에 해당 캘린더가 없음 -> 캘린더를 지우지 않고 소속만 생존 커넥션으로 안전하게 이전 (데이터 유실 원천 차단)
UPDATE external_calendars
SET external_calendar_connection_id = r.target_conn_id
WHERE external_calendar_id = cal_rec.external_calendar_id;
END IF;
END LOOP;

        -- 4. 하위 캘린더 및 이벤트가 모두 안전하게 정리·이관된 구형 중복 커넥션 삭제
DELETE FROM external_calendar_connections
WHERE user_id = r.user_id
  AND provider = r.provider
  AND external_calendar_connection_id <> r.target_conn_id;
END LOOP;
END $$;

-- 5. 유니크 제약 조건 안전하게 추가
ALTER TABLE external_calendar_connections
    DROP CONSTRAINT IF EXISTS uq_external_calendar_connections_user_provider;

ALTER TABLE external_calendar_connections
    ADD CONSTRAINT uq_external_calendar_connections_user_provider
        UNIQUE (user_id, provider);