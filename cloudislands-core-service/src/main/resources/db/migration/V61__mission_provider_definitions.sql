CREATE TABLE island_mission_definitions (
    provider_id VARCHAR(64) NOT NULL,
    mission_key VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    goal BIGINT NOT NULL,
    reward VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (mission_key),
    CONSTRAINT chk_island_mission_def_provider_not_blank CHECK (trim(provider_id) <> ''),
    CONSTRAINT chk_island_mission_def_provider_trimmed CHECK (provider_id = trim(provider_id)),
    CONSTRAINT chk_island_mission_def_key_not_blank CHECK (trim(mission_key) <> ''),
    CONSTRAINT chk_island_mission_def_key_trimmed CHECK (mission_key = trim(mission_key)),
    CONSTRAINT chk_island_mission_def_kind_known CHECK (kind IN ('MISSION', 'CHALLENGE')),
    CONSTRAINT chk_island_mission_def_title_not_blank CHECK (trim(title) <> ''),
    CONSTRAINT chk_island_mission_def_title_trimmed CHECK (title = trim(title)),
    CONSTRAINT chk_island_mission_def_reward_trimmed CHECK (reward = trim(reward)),
    CONSTRAINT chk_island_mission_def_goal_positive CHECK (goal > 0)
);

CREATE INDEX idx_island_mission_definitions_provider
    ON island_mission_definitions(provider_id, enabled, mission_key);
