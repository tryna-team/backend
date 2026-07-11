-- 기존 제약조건 삭제 (이름은 실제 DB에 생성된 이름과 일치해야 함)
ALTER TABLE events DROP CONSTRAINT IF EXISTS ck_events_external_required;

-- 새로운 제약조건 추가
ALTER TABLE events
    ADD CONSTRAINT ck_events_external_required
        CHECK (
            deleted_at IS NOT NULL
                OR source_type <> 'EXTERNAL_CALENDAR'
                OR (external_calendar_id IS NOT NULL AND external_event_id IS NOT NULL)
            );