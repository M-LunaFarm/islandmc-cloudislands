ALTER TABLE server_nodes
    ADD COLUMN max_activation_queue INTEGER NOT NULL DEFAULT 20,
    ADD COLUMN chunk_load_pressure DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN recent_failure_penalty INTEGER NOT NULL DEFAULT 0;
