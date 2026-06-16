ALTER TABLE block_values
    ADD CONSTRAINT chk_block_values_material_key_not_blank
    CHECK (trim(material_key) <> '');

ALTER TABLE block_values
    ADD CONSTRAINT chk_block_values_material_key_trimmed
    CHECK (material_key = trim(material_key));

ALTER TABLE block_values
    ADD CONSTRAINT chk_block_values_material_key_lowercase
    CHECK (material_key = lower(material_key));

ALTER TABLE block_values
    ADD CONSTRAINT chk_block_values_worth_non_negative
    CHECK (worth >= 0);

ALTER TABLE block_values
    ADD CONSTRAINT chk_block_values_level_points_non_negative
    CHECK (level_points >= 0);

ALTER TABLE block_values
    ADD CONSTRAINT chk_block_values_island_limit_non_negative
    CHECK (island_limit IS NULL OR island_limit >= 0);

ALTER TABLE island_block_counts
    ADD CONSTRAINT chk_island_block_counts_material_key_not_blank
    CHECK (trim(material_key) <> '');

ALTER TABLE island_block_counts
    ADD CONSTRAINT chk_island_block_counts_material_key_trimmed
    CHECK (material_key = trim(material_key));

ALTER TABLE island_block_counts
    ADD CONSTRAINT chk_island_block_counts_material_key_lowercase
    CHECK (material_key = lower(material_key));

ALTER TABLE island_block_counts
    ADD CONSTRAINT chk_island_block_counts_amount_non_negative
    CHECK (amount >= 0);

ALTER TABLE island_rank_snapshots
    ADD CONSTRAINT chk_island_rank_snapshots_level_non_negative
    CHECK (level >= 0);

ALTER TABLE island_rank_snapshots
    ADD CONSTRAINT chk_island_rank_snapshots_worth_non_negative
    CHECK (worth >= 0);

ALTER TABLE island_rank_snapshots
    ADD CONSTRAINT chk_island_rank_snapshots_member_count_non_negative
    CHECK (member_count >= 0);

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_logs_actor_type_not_blank
    CHECK (trim(actor_type) <> '');

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_logs_actor_type_trimmed
    CHECK (actor_type = trim(actor_type));

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_logs_action_not_blank
    CHECK (trim(action) <> '');

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_logs_action_trimmed
    CHECK (action = trim(action));

ALTER TABLE island_logs
    ADD CONSTRAINT chk_island_logs_action_not_blank
    CHECK (trim(action) <> '');

ALTER TABLE island_logs
    ADD CONSTRAINT chk_island_logs_action_trimmed
    CHECK (action = trim(action));

CREATE INDEX IF NOT EXISTS idx_island_block_counts_dirty_updated
    ON island_block_counts(updated_at ASC)
    WHERE dirty = true;

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created
    ON audit_logs(action, created_at DESC);
