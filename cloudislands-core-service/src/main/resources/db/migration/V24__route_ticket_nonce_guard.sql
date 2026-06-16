ALTER TABLE route_tickets
    ADD CONSTRAINT chk_route_tickets_nonce_not_blank
    CHECK (trim(nonce) <> '');
