CREATE TABLE IF NOT EXISTS core_event_outbox (
    event_id uuid PRIMARY KEY,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    event_type varchar(128) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    dispatched_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_core_event_outbox_version_non_negative CHECK (aggregate_version >= 0),
    CONSTRAINT chk_core_event_outbox_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT chk_core_event_outbox_status_known CHECK (status IN ('PENDING', 'DISPATCHING', 'DISPATCHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_core_event_outbox_due
    ON core_event_outbox(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_core_event_outbox_aggregate
    ON core_event_outbox(aggregate_id, aggregate_version);
