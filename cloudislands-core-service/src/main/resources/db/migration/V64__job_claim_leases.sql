ALTER TABLE island_jobs
    ADD COLUMN claim_token VARCHAR(64);

ALTER TABLE island_jobs
    ADD COLUMN claim_epoch BIGINT NOT NULL DEFAULT 0;

ALTER TABLE island_jobs
    ADD COLUMN claim_stream_id VARCHAR(128);

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_claim_token_trimmed
    CHECK (claim_token IS NULL OR claim_token = trim(claim_token));

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_claim_token_not_blank
    CHECK (claim_token IS NULL OR trim(claim_token) <> '');

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_claim_epoch_non_negative
    CHECK (claim_epoch >= 0);
