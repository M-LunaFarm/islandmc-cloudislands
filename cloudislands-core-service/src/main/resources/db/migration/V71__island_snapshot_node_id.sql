ALTER TABLE island_snapshots
    ADD COLUMN IF NOT EXISTS node_id VARCHAR(64);

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_node_id_trimmed
    CHECK (node_id IS NULL OR node_id = trim(node_id));

ALTER TABLE island_snapshots
    ADD CONSTRAINT chk_island_snapshots_node_id_not_blank
    CHECK (node_id IS NULL OR trim(node_id) <> '');
