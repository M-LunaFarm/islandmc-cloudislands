ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_ready_target
    CHECK (
        state <> 'READY'
        OR (
            target_node IS NOT NULL AND trim(target_node) <> ''
            AND target_world IS NOT NULL AND trim(target_world) <> ''
        )
    );
