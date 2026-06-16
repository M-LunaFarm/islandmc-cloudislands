ALTER TABLE island_bank
    ADD CONSTRAINT chk_island_bank_balance_non_negative
    CHECK (balance >= 0);

ALTER TABLE island_limits
    ADD CONSTRAINT chk_island_limits_key_not_blank
    CHECK (trim(limit_key) <> '');

ALTER TABLE island_limits
    ADD CONSTRAINT chk_island_limits_key_trimmed
    CHECK (limit_key = trim(limit_key));

ALTER TABLE island_limits
    ADD CONSTRAINT chk_island_limits_value_non_negative
    CHECK (limit_value >= 0);

ALTER TABLE island_upgrades
    ADD CONSTRAINT chk_island_upgrades_key_not_blank
    CHECK (trim(upgrade_key) <> '');

ALTER TABLE island_upgrades
    ADD CONSTRAINT chk_island_upgrades_key_trimmed
    CHECK (upgrade_key = trim(upgrade_key));

ALTER TABLE island_upgrades
    ADD CONSTRAINT chk_island_upgrades_level_non_negative
    CHECK (level >= 0);

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_key_not_blank
    CHECK (trim(mission_key) <> '');

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_key_trimmed
    CHECK (mission_key = trim(mission_key));

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_kind_not_blank
    CHECK (trim(kind) <> '');

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_kind_trimmed
    CHECK (kind = trim(kind));

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_title_not_blank
    CHECK (trim(title) <> '');

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_title_trimmed
    CHECK (title = trim(title));

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_reward_trimmed
    CHECK (reward = trim(reward));

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_progress_non_negative
    CHECK (progress >= 0);

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_goal_positive
    CHECK (goal > 0);

ALTER TABLE island_missions
    ADD CONSTRAINT chk_island_missions_completed_progress
    CHECK (completed = false OR progress >= goal);

CREATE INDEX IF NOT EXISTS idx_island_missions_kind_completed
    ON island_missions(kind, completed, updated_at DESC);
