CREATE TABLE IF NOT EXISTS player_preference_revisions (
    player_uuid UUID NOT NULL REFERENCES player_profiles(uuid) ON DELETE CASCADE,
    preference_key VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (player_uuid, preference_key),
    CONSTRAINT chk_player_pref_revision_key CHECK (preference_key ~ '^[a-z][a-z0-9-]{0,63}$'),
    CONSTRAINT chk_player_pref_revision_value CHECK (revision >= 0)
);
