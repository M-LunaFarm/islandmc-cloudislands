ALTER TABLE player_profiles
    ADD COLUMN IF NOT EXISTS locale VARCHAR(16) NOT NULL DEFAULT 'ko_kr';

ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_locale_not_blank
    CHECK (trim(locale) <> '');

ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_locale_trimmed
    CHECK (locale = trim(locale));

ALTER TABLE player_profiles
    ADD CONSTRAINT chk_player_profiles_locale_lowercase
    CHECK (locale = lower(locale));
