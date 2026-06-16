ALTER TABLE islands
    ADD CONSTRAINT chk_islands_state_known
    CHECK (state IN (
        'CREATE_REQUESTED',
        'CREATING',
        'INACTIVE_READY',
        'ACTIVATING',
        'RESTORING',
        'ACTIVE',
        'SAVING',
        'DELETE_REQUESTED',
        'DEACTIVATING',
        'BACKUP_BEFORE_DELETE',
        'DELETING',
        'DELETED',
        'ERROR_CREATING',
        'ERROR_ACTIVATING',
        'ERROR_SAVING',
        'QUARANTINED',
        'RECOVERY_REQUIRED'
    ));

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_size_positive
    CHECK (size > 0);

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_level_non_negative
    CHECK (level >= 0);

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_worth_non_negative
    CHECK (worth >= 0);

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_name_trimmed
    CHECK (name IS NULL OR name = trim(name));

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_name_not_blank
    CHECK (name IS NULL OR trim(name) <> '');

ALTER TABLE islands
    ADD CONSTRAINT chk_islands_deleted_at_state
    CHECK (
        (state = 'DELETED' AND deleted_at IS NOT NULL)
        OR (state <> 'DELETED')
    );

CREATE INDEX IF NOT EXISTS idx_islands_state_updated
    ON islands(state, updated_at DESC);
