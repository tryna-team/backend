-- V4: 외부 캘린더 연동 해제 시 데이터 전면 삭제(CASCADE) 정책 반영 및 매핑 테이블 연쇄 삭제 설정

-- 1. events 테이블의 외래키를 CASCADE로 변경 (external_calendars -> events)
ALTER TABLE events
    DROP CONSTRAINT IF EXISTS fk_events_external_calendar;

ALTER TABLE events
    ADD CONSTRAINT fk_events_external_calendar
        FOREIGN KEY (external_calendar_id)
            REFERENCES external_calendars (external_calendar_id)
            ON DELETE CASCADE;

-- 2. user_events 테이블의 외래키도 CASCADE로 변경 (events -> user_events)
-- 이 설정이 있어야 events가 CASCADE로 지워질 때 매핑된 user_events 레코드도 에러 없이 함께 삭제됨
ALTER TABLE user_events
    DROP CONSTRAINT IF EXISTS fk_user_events_event;

ALTER TABLE user_events
    ADD CONSTRAINT fk_user_events_event
        FOREIGN KEY (event_id)
            REFERENCES events (event_id)
            ON DELETE CASCADE;

-- 3. 외부 캘린더 연동 해제 및 Soft Delete 상태를 허용하도록 CHECK 제약 조건 안전하게 완화
ALTER TABLE events
    DROP CONSTRAINT IF EXISTS ck_events_external_required;

ALTER TABLE events
    ADD CONSTRAINT ck_events_external_required
        CHECK (
            deleted_at IS NOT NULL
                OR source_type <> 'EXTERNAL_CALENDAR'
                OR external_event_id IS NOT NULL
            );