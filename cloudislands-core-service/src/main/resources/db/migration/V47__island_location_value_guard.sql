ALTER TABLE island_homes
    ADD CONSTRAINT chk_island_homes_name_trimmed
    CHECK (name = trim(name));

ALTER TABLE island_homes
    ADD CONSTRAINT chk_island_homes_world_trimmed
    CHECK (world_name = trim(world_name));

ALTER TABLE island_homes
    ADD CONSTRAINT chk_island_homes_y_range
    CHECK (local_y BETWEEN -2048 AND 2048);

ALTER TABLE island_homes
    ADD CONSTRAINT chk_island_homes_yaw_range
    CHECK (yaw >= -360 AND yaw <= 360);

ALTER TABLE island_homes
    ADD CONSTRAINT chk_island_homes_pitch_range
    CHECK (pitch >= -90 AND pitch <= 90);

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_name_trimmed
    CHECK (name = trim(name));

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_y_range
    CHECK (local_y BETWEEN -2048 AND 2048);

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_yaw_range
    CHECK (yaw >= -360 AND yaw <= 360);

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_pitch_range
    CHECK (pitch >= -90 AND pitch <= 90);

ALTER TABLE island_biomes
    ADD CONSTRAINT chk_island_biomes_key_not_blank
    CHECK (trim(biome_key) <> '');

ALTER TABLE island_biomes
    ADD CONSTRAINT chk_island_biomes_key_trimmed
    CHECK (biome_key = trim(biome_key));

ALTER TABLE island_biomes
    ADD CONSTRAINT chk_island_biomes_key_lowercase
    CHECK (biome_key = lower(biome_key));

CREATE INDEX IF NOT EXISTS idx_island_warps_public_island
    ON island_warps(island_id, created_at DESC)
    WHERE public_access = true;
