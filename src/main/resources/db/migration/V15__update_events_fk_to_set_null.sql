-- V15: 외부 캘린더 연동 해제 시 일정(events) 데이터 보존을 위해 FK 제약조건 변경
-- 기존 ON DELETE CASCADE (V5에서 추가됨) -> ERD 정책에 따라 ON DELETE SET NULL 로 변경

-- 1. 기존 외래키 제약조건 제거
ALTER TABLE events
    DROP CONSTRAINT IF EXISTS fk_events_external_calendar;

-- 2. ON DELETE SET NULL 옵션을 포함하여 외래키 제약조건 재생성
ALTER TABLE events
    ADD CONSTRAINT fk_events_external_calendar
        FOREIGN KEY (external_calendar_id)
            REFERENCES external_calendars (external_calendar_id)
            ON DELETE SET NULL;