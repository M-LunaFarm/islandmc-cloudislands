ALTER TABLE island_roles
    DROP CONSTRAINT IF EXISTS chk_island_roles_role_known;

ALTER TABLE island_roles
    ADD CONSTRAINT chk_island_roles_role_known
    CHECK (role IN (
        'CO_OWNER',
        'MODERATOR',
        'MEMBER',
        'TRUSTED',
        'CUSTOM_1',
        'CUSTOM_2',
        'CUSTOM_3',
        'CUSTOM_4',
        'CUSTOM_5'
    ));
