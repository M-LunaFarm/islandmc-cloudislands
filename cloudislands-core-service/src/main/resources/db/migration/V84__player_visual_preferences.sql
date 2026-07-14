ALTER TABLE player_profiles
    ADD COLUMN world_border_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN blocks_stacker_enabled BOOLEAN NOT NULL DEFAULT TRUE;
