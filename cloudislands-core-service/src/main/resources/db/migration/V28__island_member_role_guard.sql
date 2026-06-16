ALTER TABLE island_members
    ADD CONSTRAINT chk_island_members_role_known
    CHECK (role IN ('OWNER', 'CO_OWNER', 'MODERATOR', 'MEMBER', 'TRUSTED', 'VISITOR', 'BANNED'));
