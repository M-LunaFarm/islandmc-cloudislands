ALTER TABLE island_members
    DROP CONSTRAINT IF EXISTS chk_island_members_role_known;

ALTER TABLE island_members
    ADD CONSTRAINT chk_island_members_role_known
    CHECK (role IN (
        'OWNER',
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
