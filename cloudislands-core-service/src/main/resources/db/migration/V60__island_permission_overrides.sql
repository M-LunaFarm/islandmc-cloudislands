CREATE TABLE island_permission_overrides (
    island_id UUID NOT NULL REFERENCES islands(id),
    player_uuid UUID NOT NULL,
    permission_key VARCHAR(64) NOT NULL,
    value BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, player_uuid, permission_key),
    CONSTRAINT chk_island_permission_override_key_known CHECK (permission_key IN (
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
    ))
);

CREATE INDEX idx_island_permission_overrides_player
    ON island_permission_overrides(player_uuid, island_id);
