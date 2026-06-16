ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_fencing_token_non_negative
    CHECK (fencing_token >= 0);

ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_active_fencing_token_positive
    CHECK (
        state NOT IN ('ACTIVE', 'ACTIVATING', 'RESTORING', 'SAVING', 'DEACTIVATING')
        OR fencing_token > 0
    );

ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_active_node_trimmed
    CHECK (active_node IS NULL OR active_node = trim(active_node));

ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_active_world_trimmed
    CHECK (active_world IS NULL OR active_world = trim(active_world));

ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_lease_owner_trimmed
    CHECK (lease_owner IS NULL OR lease_owner = trim(lease_owner));

ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_lease_owner_not_blank
    CHECK (lease_owner IS NULL OR trim(lease_owner) <> '');

ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_lease_until_has_owner
    CHECK (lease_until IS NULL OR lease_owner IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_island_runtime_active_node_state
    ON island_runtime(active_node, state, updated_at DESC)
    WHERE active_node IS NOT NULL;
