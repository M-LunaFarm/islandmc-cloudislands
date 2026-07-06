ALTER TABLE player_profiles
    ADD COLUMN IF NOT EXISTS disbands_remaining INTEGER NOT NULL DEFAULT 0;

ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_disbands_remaining_nonnegative
    CHECK (disbands_remaining >= 0);
