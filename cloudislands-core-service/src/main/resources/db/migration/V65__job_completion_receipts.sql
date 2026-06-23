CREATE TABLE job_completion_receipts (
    job_id UUID PRIMARY KEY REFERENCES island_jobs(id) ON DELETE CASCADE,
    receipt_id UUID NOT NULL UNIQUE,
    job_type VARCHAR(64) NOT NULL,
    island_id UUID NOT NULL REFERENCES islands(id),
    target_node VARCHAR(64),
    claimant_node VARCHAR(64) NOT NULL,
    claim_token VARCHAR(64) NOT NULL,
    claim_epoch BIGINT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    request_payload JSONB NOT NULL,
    receipt_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    completion_status VARCHAR(32) NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    committed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    acked_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_job_completion_receipts_type_known CHECK (job_type IN ('CREATE_ISLAND', 'ACTIVATE_ISLAND', 'DEACTIVATE_ISLAND', 'SAVE_ISLAND', 'SNAPSHOT_ISLAND', 'MIGRATE_ISLAND', 'DELETE_ISLAND', 'RESET_ISLAND', 'RESTORE_ISLAND')),
    CONSTRAINT chk_job_completion_receipts_target_node_trimmed CHECK (target_node IS NULL OR target_node = trim(target_node)),
    CONSTRAINT chk_job_completion_receipts_claimant_node_trimmed CHECK (claimant_node = trim(claimant_node)),
    CONSTRAINT chk_job_completion_receipts_claimant_node_not_blank CHECK (trim(claimant_node) <> ''),
    CONSTRAINT chk_job_completion_receipts_claim_token_trimmed CHECK (claim_token = trim(claim_token)),
    CONSTRAINT chk_job_completion_receipts_claim_token_not_blank CHECK (trim(claim_token) <> ''),
    CONSTRAINT chk_job_completion_receipts_claim_epoch_positive CHECK (claim_epoch > 0),
    CONSTRAINT chk_job_completion_receipts_request_hash_hex CHECK (request_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT chk_job_completion_receipts_status_known CHECK (completion_status IN ('COMPLETED', 'FAILED')),
    CONSTRAINT chk_job_completion_receipts_aggregate_version_non_negative CHECK (aggregate_version >= 0),
    CONSTRAINT chk_job_completion_receipts_ack_after_commit CHECK (acked_at IS NULL OR acked_at >= committed_at)
);

CREATE INDEX idx_job_completion_receipts_island
    ON job_completion_receipts(island_id, committed_at DESC);

CREATE INDEX idx_job_completion_receipts_request_hash
    ON job_completion_receipts(request_hash);
