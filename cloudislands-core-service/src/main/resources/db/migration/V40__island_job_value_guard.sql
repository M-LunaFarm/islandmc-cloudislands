ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_type_known
    CHECK (job_type IN (
        'CREATE_ISLAND',
        'ACTIVATE_ISLAND',
        'DEACTIVATE_ISLAND',
        'SAVE_ISLAND',
        'SNAPSHOT_ISLAND',
        'MIGRATE_ISLAND',
        'DELETE_ISLAND',
        'RESET_ISLAND',
        'RESTORE_ISLAND'
    ));

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_state_known
    CHECK (state IN ('PENDING', 'CLAIMED', 'COMPLETED', 'FAILED', 'CANCELED'));

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_request_id_trimmed
    CHECK (request_id = trim(request_id));

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_target_node_trimmed
    CHECK (target_node IS NULL OR target_node = trim(target_node));

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_locked_by_trimmed
    CHECK (locked_by IS NULL OR locked_by = trim(locked_by));

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_locked_by_not_blank
    CHECK (locked_by IS NULL OR trim(locked_by) <> '');

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_claimed_has_lock
    CHECK (
        state <> 'CLAIMED'
        OR (locked_by IS NOT NULL AND locked_until IS NOT NULL)
    );

ALTER TABLE island_jobs
    ADD CONSTRAINT chk_island_jobs_lock_requires_claimed
    CHECK (
        locked_by IS NULL
        OR state = 'CLAIMED'
    );

CREATE INDEX IF NOT EXISTS idx_island_jobs_pending_claim
    ON island_jobs(target_node, priority DESC, created_at ASC)
    WHERE state = 'PENDING';
