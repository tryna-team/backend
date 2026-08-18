-- V20: 공휴일 다중 서버 동시성(Race Condition) 중복 삽입 방어 로직 추가

-- 1. 중복된 데이터가 남아있으면 Unique Index 생성이 실패하므로, 기존 공휴일 데이터를 깔끔하게 초기화
DELETE FROM events WHERE source_type = 'HOLIDAY';

-- 2. DB 레벨에서 '동일한 외부 공휴일 ID'가 2번 들어오는 것을 원천 차단하는 방어막 생성
CREATE UNIQUE INDEX uq_events_holiday_external_id
    ON events (external_event_id)
    WHERE source_type = 'HOLIDAY';