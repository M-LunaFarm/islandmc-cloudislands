ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_action_known
    CHECK (action IN ('HOME', 'VISIT', 'WARP', 'ADMIN_TELEPORT', 'RETURN_AFTER_MIGRATION'));

ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_state_known
    CHECK (state IN ('PREPARING', 'READY', 'CONSUMED', 'EXPIRED', 'CANCELLED', 'FAILED'));

ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_target_node_trimmed
    CHECK (target_node IS NULL OR target_node = trim(target_node));

ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_target_world_trimmed
    CHECK (target_world IS NULL OR target_world = trim(target_world));

ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_nonce_trimmed
    CHECK (nonce = trim(nonce));

CREATE INDEX IF NOT EXISTS idx_route_tickets_island_state
    ON route_tickets(island_id, state, expires_at);
