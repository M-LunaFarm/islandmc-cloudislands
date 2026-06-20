ALTER TABLE island_members
    DROP CONSTRAINT IF EXISTS chk_island_members_role_known;

ALTER TABLE island_members
    ADD CONSTRAINT chk_island_members_role_key_format
    CHECK (
        role = trim(role)
        AND role = upper(role)
        AND role <> ''
        AND role !~ '[^A-Z0-9_]'
        AND length(role) <= 32
        AND role NOT IN ('VISITOR', 'BANNED')
    );
