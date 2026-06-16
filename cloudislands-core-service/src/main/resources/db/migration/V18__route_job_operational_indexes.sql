CREATE INDEX IF NOT EXISTS idx_route_tickets_island_node_state
    ON route_tickets(island_id, target_node, state, expires_at);

CREATE INDEX IF NOT EXISTS idx_route_tickets_expiry
    ON route_tickets(state, expires_at)
    WHERE state IN ('READY', 'PREPARING');

CREATE INDEX IF NOT EXISTS idx_island_jobs_claim
    ON island_jobs(target_node, state, priority DESC, created_at)
    WHERE state IN ('QUEUED', 'RETRY_READY');

CREATE INDEX IF NOT EXISTS idx_island_jobs_island_state
    ON island_jobs(island_id, state, updated_at DESC)
    WHERE island_id IS NOT NULL;
