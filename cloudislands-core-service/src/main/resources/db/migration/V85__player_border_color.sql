ALTER TABLE player_profiles
    ADD COLUMN border_color VARCHAR(16) NOT NULL DEFAULT 'blue';

ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_border_color
    CHECK (border_color IN ('blue', 'green', 'red'));
