ALTER TABLE action_items
    ADD COLUMN occurrence_date DATE;

UPDATE action_items ai
SET occurrence_date = COALESCE(e.start_date, DATE(e.start_datetime))
    FROM events e
WHERE ai.parent_event_id = e.event_id;

ALTER TABLE action_items
    ALTER COLUMN occurrence_date SET NOT NULL;

CREATE INDEX idx_action_items_parent_event_occurrence
    ON action_items(parent_event_id, occurrence_date);