ALTER TABLE island_roles
    DROP CONSTRAINT IF EXISTS chk_island_roles_role_known;

ALTER TABLE island_roles
    ADD CONSTRAINT chk_island_roles_role_key_format
    CHECK (
        role = trim(role)
        AND role = upper(role)
        AND role <> ''
        AND role !~ '[^A-Z0-9_]'
        AND length(role) <= 32
        AND role NOT IN ('OWNER', 'VISITOR', 'BANNED')
    );

ALTER TABLE island_permissions
    DROP CONSTRAINT IF EXISTS chk_island_permissions_role_known;

ALTER TABLE island_permissions
    ADD CONSTRAINT chk_island_permissions_role_key_format
    CHECK (
        role = trim(role)
        AND role = upper(role)
        AND role <> ''
        AND role !~ '[^A-Z0-9_]'
        AND length(role) <= 32
    );
