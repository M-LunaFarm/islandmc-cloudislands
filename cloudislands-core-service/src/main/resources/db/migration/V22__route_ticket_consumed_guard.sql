ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_consumed_at
    CHECK (
        state <> 'CONSUMED'
        OR consumed_at IS NOT NULL
    );
