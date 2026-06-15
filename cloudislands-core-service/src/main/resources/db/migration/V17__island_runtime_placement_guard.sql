CREATE UNIQUE INDEX IF NOT EXISTS idx_island_runtime_active_placement
    ON island_runtime(active_world, cell_x, cell_z)
    WHERE active_world IS NOT NULL
      AND cell_x IS NOT NULL
      AND cell_z IS NOT NULL
      AND state IN ('ACTIVE', 'ACTIVATING', 'RESTORING', 'SAVING', 'DEACTIVATING');
