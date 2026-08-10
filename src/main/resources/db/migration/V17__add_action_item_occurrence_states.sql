CREATE TABLE action_item_occurrence_states (
    action_item_occurrence_state_id BIGSERIAL PRIMARY KEY,
    action_item_id BIGINT NOT NULL,
    occurrence_date DATE NOT NULL,
    action_item_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_action_item_occurrence_states_action_item
        FOREIGN KEY (action_item_id)
        REFERENCES action_items(action_item_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_action_item_occurrence_states_item_date
        UNIQUE (action_item_id, occurrence_date),
    CONSTRAINT ck_action_item_occurrence_states_status
        CHECK (action_item_status IN ('PENDING', 'COMPLETED', 'NEEDS_CONFIRMATION', 'DELETED'))
);

CREATE INDEX idx_action_item_occurrence_states_date
    ON action_item_occurrence_states(occurrence_date);
INSERT INTO action_item_occurrence_states (
    action_item_id,
    occurrence_date,
    action_item_status,
    completed_at,
    created_at,
    updated_at
)
SELECT
    a.action_item_id,
    a.occurrence_date,
    a.action_item_status,
    a.completed_at,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM action_items a
JOIN events e ON e.event_id = a.parent_event_id
WHERE e.is_recurring = true
  AND a.occurrence_date IS NOT NULL
  AND a.action_item_status = 'COMPLETED'
ON CONFLICT (action_item_id, occurrence_date) DO NOTHING;

UPDATE action_items a
SET action_item_status = 'PENDING',
    completed_at = NULL,
    updated_at = CURRENT_TIMESTAMP
FROM events e
WHERE e.event_id = a.parent_event_id
  AND e.is_recurring = true
  AND a.occurrence_date IS NOT NULL
  AND a.action_item_status = 'COMPLETED';
