ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_storage_path_trimmed
    CHECK (storage_path = trim(storage_path));

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_reason_trimmed
    CHECK (reason = trim(reason));

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_checksum_trimmed
    CHECK (checksum IS NULL OR checksum = trim(checksum));

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_checksum_not_blank
    CHECK (checksum IS NULL OR trim(checksum) <> '');

CREATE INDEX IF NOT EXISTS idx_island_snapshots_latest
    ON island_snapshots(island_id, snapshot_no DESC, created_at DESC);
