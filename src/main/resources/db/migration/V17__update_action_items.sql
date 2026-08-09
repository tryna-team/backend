DELETE FROM action_items ai
    USING events e
WHERE ai.parent_event_id = e.event_id
  AND e.start_date IS NULL
  AND e.start_datetime IS NULL;