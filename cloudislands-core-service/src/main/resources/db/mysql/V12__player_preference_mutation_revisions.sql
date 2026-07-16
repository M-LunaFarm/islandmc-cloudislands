CREATE TABLE IF NOT EXISTS player_preference_revisions (
    player_uuid CHAR(36) NOT NULL,
    preference_key VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (player_uuid, preference_key),
    CONSTRAINT fk_player_pref_revision_profile FOREIGN KEY (player_uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE,
    CONSTRAINT chk_player_pref_revision_key CHECK (preference_key REGEXP '^[a-z][a-z0-9-]{0,63}$'),
    CONSTRAINT chk_player_pref_revision_value CHECK (revision >= 0)
);
