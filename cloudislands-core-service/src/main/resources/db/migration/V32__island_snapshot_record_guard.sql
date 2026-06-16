ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_no_positive
    CHECK (snapshot_no > 0);

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_storage_path_not_blank
    CHECK (trim(storage_path) <> '');

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_reason_not_blank
    CHECK (trim(reason) <> '');

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_size_non_negative
    CHECK (size_bytes IS NULL OR size_bytes >= 0);
