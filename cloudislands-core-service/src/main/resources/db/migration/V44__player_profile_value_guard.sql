ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_last_name_trimmed
    CHECK (last_name IS NULL OR last_name = trim(last_name));

ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_last_name_not_blank
    CHECK (last_name IS NULL OR trim(last_name) <> '');

ALTER TABLE player_profiles
    ADD CONSTRAINT fk_player_profiles_primary_island
    FOREIGN KEY (primary_island_id)
    REFERENCES islands(id)
    NOT VALID;

CREATE INDEX IF NOT EXISTS idx_player_profiles_last_name_ci
    ON player_profiles(lower(last_name), last_seen_at DESC)
    WHERE last_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_player_profiles_primary_island
    ON player_profiles(primary_island_id)
    WHERE primary_island_id IS NOT NULL;
