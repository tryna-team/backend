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
        CHECK (action_item_status IN ('PENDING', 'COMPLETED'))
);

CREATE INDEX idx_action_item_occurrence_states_date
    ON action_item_occurrence_states(occurrence_date);
