ALTER TABLE island_warps
    ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'default';

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_category_not_blank
    CHECK (trim(category) <> '');

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_category_trimmed
    CHECK (category = trim(category));

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_category_lowercase
    CHECK (category = lower(category));

CREATE INDEX IF NOT EXISTS idx_island_warps_public_category_recent
    ON island_warps(public_access, category, created_at DESC);
