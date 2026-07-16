ALTER TABLE player_profiles
    ADD COLUMN primary_island_selection_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_primary_island_selection_revision_nonnegative
    CHECK (primary_island_selection_revision >= 0);
