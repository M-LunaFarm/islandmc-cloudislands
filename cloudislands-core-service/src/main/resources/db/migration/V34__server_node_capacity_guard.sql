ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_identity_not_blank
    CHECK (trim(id) <> '' AND trim(pool) <> '' AND trim(velocity_server_name) <> '' AND trim(state) <> '');

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_capacity_non_negative
    CHECK (
        soft_player_cap >= 0
        AND hard_player_cap >= 0
        AND reserved_slots >= 0
        AND max_active_islands >= 0
        AND players >= 0
        AND active_islands >= 0
        AND activation_queue >= 0
    );

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_capacity_bounds
    CHECK (
        hard_player_cap = 0
        OR soft_player_cap = 0
        OR soft_player_cap <= hard_player_cap
    );

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_metrics_non_negative
    CHECK (
        (mspt IS NULL OR mspt >= 0)
        AND (heap_used_mb IS NULL OR heap_used_mb >= 0)
        AND (heap_max_mb IS NULL OR heap_max_mb >= 0)
    );
