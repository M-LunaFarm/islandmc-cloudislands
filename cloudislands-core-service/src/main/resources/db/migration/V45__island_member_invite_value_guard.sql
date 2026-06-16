ALTER TABLE island_invites
    ADD CONSTRAINT chk_island_invites_state_known
    CHECK (state IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED'));

ALTER TABLE island_invites
    ADD CONSTRAINT chk_island_invites_not_self_invite
    CHECK (inviter_uuid <> target_uuid);

ALTER TABLE island_invites
    ADD CONSTRAINT chk_island_invites_expires_after_created
    CHECK (expires_at > created_at);

ALTER TABLE island_members
    ADD CONSTRAINT chk_island_members_joined_at_present
    CHECK (joined_at IS NOT NULL);

CREATE UNIQUE INDEX IF NOT EXISTS idx_island_members_one_owner
    ON island_members(island_id)
    WHERE role = 'OWNER';

CREATE INDEX IF NOT EXISTS idx_island_invites_island_state
    ON island_invites(island_id, state, created_at DESC);
