ALTER TABLE island_members
    ADD COLUMN IF NOT EXISTS trusted_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_island_members_trusted_expiry
    ON island_members(trusted_expires_at)
    WHERE trusted_expires_at IS NOT NULL;
