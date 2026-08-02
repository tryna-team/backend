-- V4: 외부 캘린더 연동 해제 시 CASCADE 정책 및 제약조건 추가 (NOT VALID로 락 방지)

-- 1. events 테이블 외래키 추가 (NOT VALID)
ALTER TABLE events
    DROP CONSTRAINT IF EXISTS fk_events_external_calendar;

ALTER TABLE events
    ADD CONSTRAINT fk_events_external_calendar
        FOREIGN KEY (external_calendar_id)
            REFERENCES external_calendars (external_calendar_id)
            ON DELETE CASCADE
    NOT VALID;

-- 2. user_events 테이블 외래키 추가 (NOT VALID)
ALTER TABLE user_events
    DROP CONSTRAINT IF EXISTS fk_user_events_event;

ALTER TABLE user_events
    ADD CONSTRAINT fk_user_events_event
        FOREIGN KEY (event_id)
            REFERENCES events (event_id)
            ON DELETE CASCADE
    NOT VALID;

-- 3. CHECK 제약조건 추가 (NOT VALID)
ALTER TABLE events
    DROP CONSTRAINT IF EXISTS ck_events_external_required;

ALTER TABLE events
    ADD CONSTRAINT ck_events_external_required
        CHECK (
            deleted_at IS NOT NULL
                OR source_type <> 'EXTERNAL_CALENDAR'
                OR (external_calendar_id IS NOT NULL AND external_event_id IS NOT NULL)
            )
    NOT VALID;