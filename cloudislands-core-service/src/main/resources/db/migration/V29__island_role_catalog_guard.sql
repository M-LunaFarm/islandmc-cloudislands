ALTER TABLE island_roles
    ADD CONSTRAINT chk_island_roles_role_known
    CHECK (role IN ('OWNER', 'CO_OWNER', 'MODERATOR', 'MEMBER', 'TRUSTED', 'VISITOR', 'BANNED'));

CREATE UNIQUE INDEX IF NOT EXISTS idx_island_roles_weight_unique
ON island_roles(island_id, weight);
