ALTER TABLE island_bans
    ADD CONSTRAINT chk_island_bans_reason_trimmed
    CHECK (reason IS NULL OR reason = trim(reason));

ALTER TABLE island_bans
    ADD CONSTRAINT chk_island_bans_reason_not_blank
    CHECK (reason IS NULL OR trim(reason) <> '');

ALTER TABLE island_bans
    ADD CONSTRAINT chk_island_bans_expires_after_created
    CHECK (expires_at IS NULL OR expires_at > created_at);

CREATE INDEX IF NOT EXISTS idx_island_bans_active_lookup
    ON island_bans(island_id, banned_uuid)
    WHERE expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_island_bans_expiring
    ON island_bans(expires_at)
    WHERE expires_at IS NOT NULL;
