CREATE INDEX IF NOT EXISTS idx_route_tickets_target_node_active
    ON route_tickets(target_node, state, expires_at)
    WHERE target_node IS NOT NULL
      AND state IN ('PREPARING', 'READY');
