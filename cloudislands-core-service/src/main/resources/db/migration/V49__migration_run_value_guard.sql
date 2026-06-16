ALTER TABLE migration_runs
    ADD CONSTRAINT chk_migration_runs_source_not_blank
    CHECK (trim(source) <> '');

ALTER TABLE migration_runs
    ADD CONSTRAINT chk_migration_runs_source_trimmed
    CHECK (source = trim(source));

ALTER TABLE migration_runs
    ADD CONSTRAINT chk_migration_runs_state_known
    CHECK (state IN (
        'SCANNED',
        'DRY_RUN_FAILED',
        'DRY_RUN_PASSED',
        'EXTRACT_FAILED',
        'EXTRACTED',
        'IMPORTING',
        'IMPORTED',
        'VERIFYING',
        'VERIFIED',
        'ROLLED_BACK'
    ));

ALTER TABLE migration_runs
    ADD CONSTRAINT chk_migration_runs_scanned_islands_non_negative
    CHECK (scanned_islands >= 0);

ALTER TABLE migration_runs
    ADD CONSTRAINT chk_migration_runs_blocking_issues_non_negative
    CHECK (blocking_issues >= 0);

ALTER TABLE migration_runs
    ADD CONSTRAINT chk_migration_runs_manifest_path_trimmed
    CHECK (manifest_path IS NULL OR manifest_path = trim(manifest_path));

ALTER TABLE migration_runs
    ADD CONSTRAINT chk_migration_runs_manifest_path_not_blank
    CHECK (manifest_path IS NULL OR trim(manifest_path) <> '');

CREATE INDEX IF NOT EXISTS idx_migration_runs_source_state_updated
    ON migration_runs(source, state, updated_at DESC);
