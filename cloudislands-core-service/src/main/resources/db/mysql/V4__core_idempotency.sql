CREATE TABLE IF NOT EXISTS core_idempotency (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    response_status INTEGER NULL,
    response_content_type VARCHAR(255) NULL,
    response_body LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_core_idempotency_state CHECK (state IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_core_idempotency_response CHECK (
        (state = 'PENDING' AND response_status IS NULL)
        OR (state = 'COMPLETED' AND response_status BETWEEN 100 AND 599 AND response_body IS NOT NULL)
    ),
    INDEX idx_core_idempotency_updated_at (updated_at)
);
