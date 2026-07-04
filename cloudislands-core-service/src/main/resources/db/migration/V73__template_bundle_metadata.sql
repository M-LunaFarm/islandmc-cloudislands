ALTER TABLE island_templates
    ADD COLUMN IF NOT EXISTS description VARCHAR(512) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS category VARCHAR(64) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS required_permission VARCHAR(128) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS icon_material VARCHAR(64) NOT NULL DEFAULT 'GRASS_BLOCK',
    ADD COLUMN IF NOT EXISTS icon_custom_model_data INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS preview_image_key VARCHAR(256) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS bundle_storage_path VARCHAR(512) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS bundle_checksum VARCHAR(128) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS bundle_size_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS schema_version INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS default_island_size INTEGER NOT NULL DEFAULT 300,
    ADD COLUMN IF NOT EXISTS spawn_world_offset_x DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    ADD COLUMN IF NOT EXISTS spawn_world_offset_y DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    ADD COLUMN IF NOT EXISTS spawn_world_offset_z DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    ADD COLUMN IF NOT EXISTS spawn_yaw DOUBLE PRECISION NOT NULL DEFAULT 180.0,
    ADD COLUMN IF NOT EXISTS spawn_pitch DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS home_name VARCHAR(64) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS environment_preset VARCHAR(64) NOT NULL DEFAULT 'normal',
    ADD COLUMN IF NOT EXISTS biome_key VARCHAR(96) NOT NULL DEFAULT 'minecraft:plains',
    ADD COLUMN IF NOT EXISTS border_color VARCHAR(32) NOT NULL DEFAULT 'BLUE',
    ADD COLUMN IF NOT EXISTS bank_initial_balance VARCHAR(48) NOT NULL DEFAULT '0',
    ADD COLUMN IF NOT EXISTS creation_cost VARCHAR(48) NOT NULL DEFAULT '0',
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tags_csv VARCHAR(512) NOT NULL DEFAULT '';

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_bundle_size_non_negative
    CHECK (bundle_size_bytes >= 0);

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_schema_version_positive
    CHECK (schema_version > 0);

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_default_size_positive
    CHECK (default_island_size > 0);

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_icon_model_non_negative
    CHECK (icon_custom_model_data >= 0);

ALTER TABLE island_templates
    ADD CONSTRAINT chk_island_templates_sort_order_non_negative
    CHECK (sort_order >= 0);

CREATE INDEX IF NOT EXISTS idx_island_templates_enabled_order
    ON island_templates(enabled, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_island_templates_category_order
    ON island_templates(category, sort_order, id);
