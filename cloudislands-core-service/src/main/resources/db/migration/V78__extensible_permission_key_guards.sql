ALTER TABLE island_permissions
    DROP CONSTRAINT IF EXISTS chk_island_permissions_key_known;

ALTER TABLE island_permissions
    ADD CONSTRAINT chk_island_permissions_key_format
    CHECK (
        permission_key = trim(permission_key)
        AND permission_key = upper(permission_key)
        AND permission_key ~ '^[A-Z][A-Z0-9_]{0,63}$'
    );

ALTER TABLE island_permission_overrides
    DROP CONSTRAINT IF EXISTS chk_island_permission_override_key_known;

ALTER TABLE island_permission_overrides
    ADD CONSTRAINT chk_island_permission_override_key_format
    CHECK (
        permission_key = trim(permission_key)
        AND permission_key = upper(permission_key)
        AND permission_key ~ '^[A-Z][A-Z0-9_]{0,63}$'
    );
