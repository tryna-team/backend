CREATE TABLE recurring_event_exceptions (
    recurring_event_exception_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    occurrence_date DATE NOT NULL,
    exception_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recurring_event_exceptions_event
        FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE,
    CONSTRAINT uq_recurring_event_exceptions_event_date_type
        UNIQUE (event_id, occurrence_date, exception_type)
);

CREATE INDEX idx_recurring_event_exceptions_event_date
    ON recurring_event_exceptions(event_id, occurrence_date);
