ALTER TABLE island_runtime
    ADD CONSTRAINT chk_island_runtime_active_location
    CHECK (
        state NOT IN ('ACTIVE', 'ACTIVATING', 'RESTORING', 'SAVING', 'DEACTIVATING')
        OR (
            active_node IS NOT NULL AND trim(active_node) <> ''
            AND active_world IS NOT NULL AND trim(active_world) <> ''
            AND cell_x IS NOT NULL
            AND cell_z IS NOT NULL
        )
    );
