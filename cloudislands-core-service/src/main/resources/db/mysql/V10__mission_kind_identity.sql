ALTER TABLE island_mission_definitions ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'general';
ALTER TABLE island_mission_definitions ADD COLUMN description VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE island_mission_definitions ADD COLUMN trigger_type VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE island_mission_definitions ADD COLUMN target_key VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE island_mission_definitions ADD COLUMN reward_type VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE island_mission_definitions ADD COLUMN repeatable BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE island_mission_definitions ADD COLUMN daily_reset BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE island_missions ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'general';
ALTER TABLE island_missions ADD COLUMN description VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE island_missions ADD COLUMN trigger_type VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE island_missions ADD COLUMN target_key VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE island_missions ADD COLUMN reward_type VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE island_missions ADD COLUMN repeatable BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE island_missions ADD COLUMN daily_reset BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE island_missions
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (island_id, mission_key, kind);

ALTER TABLE island_mission_definitions
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (mission_key, kind);
