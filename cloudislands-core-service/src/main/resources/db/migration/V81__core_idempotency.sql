CREATE TABLE IF NOT EXISTS core_idempotency (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    response_status INTEGER,
    response_content_type VARCHAR(255),
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_core_idempotency_state CHECK (state IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_core_idempotency_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_core_idempotency_response CHECK (
        (state = 'PENDING' AND response_status IS NULL)
        OR (state = 'COMPLETED' AND response_status BETWEEN 100 AND 599 AND response_body IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_core_idempotency_updated_at
    ON core_idempotency(updated_at);
