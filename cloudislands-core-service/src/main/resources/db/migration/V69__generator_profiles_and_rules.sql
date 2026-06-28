CREATE TABLE IF NOT EXISTS island_generator_profiles (
    island_id UUID PRIMARY KEY,
    generator_key VARCHAR(64) NOT NULL DEFAULT 'default',
    level INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS generator_rules (
    generator_key VARCHAR(64) NOT NULL,
    material_key VARCHAR(128) NOT NULL,
    chance DOUBLE PRECISION NOT NULL,
    min_island_level INTEGER NOT NULL DEFAULT 0,
    min_upgrade_level INTEGER NOT NULL DEFAULT 1,
    biome_key VARCHAR(64) NOT NULL DEFAULT '*',
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (generator_key, material_key, min_island_level, min_upgrade_level, biome_key)
);
