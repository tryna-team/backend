ALTER TABLE action_items
    ADD COLUMN occurrence_date DATE;

-- 날짜를 확정할 수 없는 기존 action-item 제거
DELETE FROM action_items ai
    USING events e
WHERE ai.parent_event_id = e.event_id
  AND e.start_date IS NULL
  AND e.start_datetime IS NULL;

-- 부모 일정 날짜를 기존 action-item의 occurrenceDate로 backfill
UPDATE action_items ai
SET occurrence_date = COALESCE(
        e.start_date,
        DATE(e.start_datetime)
                      )
    FROM events e
WHERE ai.parent_event_id = e.event_id;

-- 모든 기존 row의 occurrenceDate 확보 후 NOT NULL 적용
ALTER TABLE action_items
    ALTER COLUMN occurrence_date SET NOT NULL;

CREATE INDEX idx_action_items_parent_event_occurrence
    ON action_items(parent_event_id, occurrence_date);ㅎ