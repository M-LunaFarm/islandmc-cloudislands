ALTER TABLE island_permissions
    ADD CONSTRAINT chk_island_permissions_key_not_blank
    CHECK (trim(permission_key) <> '');

ALTER TABLE island_flags
    ADD CONSTRAINT chk_island_flags_key_not_blank
    CHECK (trim(flag_key) <> '');

ALTER TABLE island_flags
    ADD CONSTRAINT chk_island_flags_value_not_blank
    CHECK (trim(flag_value) <> '');
