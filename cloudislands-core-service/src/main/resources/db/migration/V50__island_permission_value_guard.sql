ALTER TABLE island_roles
    DROP CONSTRAINT IF EXISTS chk_island_roles_role_known;

ALTER TABLE island_roles
    ADD CONSTRAINT chk_island_roles_role_known
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
        'CUSTOM_5',
        'VISITOR',
        'BANNED'
    ));

ALTER TABLE island_roles
    ADD CONSTRAINT chk_island_roles_display_name_trimmed
    CHECK (display_name IS NULL OR display_name = trim(display_name));

ALTER TABLE island_roles
    ADD CONSTRAINT chk_island_roles_display_name_not_blank
    CHECK (display_name IS NULL OR trim(display_name) <> '');

ALTER TABLE island_permissions
    ADD CONSTRAINT chk_island_permissions_role_known
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
        'CUSTOM_5',
        'VISITOR',
        'BANNED'
    ));

ALTER TABLE island_permissions
    ADD CONSTRAINT chk_island_permissions_key_known
    CHECK (permission_key IN (
        'BUILD',
        'BREAK',
        'INTERACT',
        'OPEN_CONTAINER',
        'USE_DOOR',
        'USE_BUTTON',
        'USE_PRESSURE_PLATE',
        'USE_REDSTONE',
        'PLACE_LIQUID',
        'BREAK_LIQUID',
        'ATTACK_PLAYER',
        'ATTACK_MOB',
        'PICKUP_ITEM',
        'DROP_ITEM',
        'USE_SPAWNER',
        'USE_ANVIL',
        'USE_ENCHANT_TABLE',
        'USE_BREWING_STAND',
        'MANAGE_MEMBERS',
        'MANAGE_ROLES',
        'MANAGE_FLAGS',
        'MANAGE_WARPS',
        'MANAGE_UPGRADES',
        'START_LEVEL_CALC',
        'BAN_VISITOR',
        'KICK_VISITOR',
        'SET_HOME',
        'SET_BIOME',
        'WITHDRAW_BANK',
        'DEPOSIT_BANK'
    ));

ALTER TABLE island_permissions
    ADD CONSTRAINT chk_island_permissions_key_trimmed
    CHECK (permission_key = trim(permission_key));

ALTER TABLE island_flags
    ADD CONSTRAINT chk_island_flags_key_trimmed
    CHECK (flag_key = trim(flag_key));

ALTER TABLE island_flags
    ADD CONSTRAINT chk_island_flags_value_trimmed
    CHECK (flag_value = trim(flag_value));
