ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_routing_pressure_non_negative
    CHECK (
        max_activation_queue >= 0
        AND chunk_load_pressure >= 0
        AND recent_failure_penalty >= 0
    );

ALTER TABLE server_nodes
    ADD CONSTRAINT chk_server_nodes_activation_queue_bounds
    CHECK (
        max_activation_queue = 0
        OR activation_queue <= max_activation_queue
    );
