CREATE TABLE IF NOT EXISTS island_templates (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    min_node_version VARCHAR(64),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_island_templates_id_not_blank CHECK (trim(id) <> ''),
    CONSTRAINT chk_island_templates_id_trimmed CHECK (id = trim(id)),
    CONSTRAINT chk_island_templates_id_lowercase CHECK (id = lower(id)),
    CONSTRAINT chk_island_templates_display_name_not_blank CHECK (trim(display_name) <> ''),
    CONSTRAINT chk_island_templates_display_name_trimmed CHECK (display_name = trim(display_name)),
    CONSTRAINT chk_island_templates_min_node_version_trimmed CHECK (min_node_version IS NULL OR min_node_version = trim(min_node_version)),
    CONSTRAINT chk_island_templates_min_node_version_not_blank CHECK (min_node_version IS NULL OR trim(min_node_version) <> '')
);

INSERT IGNORE INTO island_templates(id, display_name, enabled)
VALUES
    ('default', 'Default Island', true),
    ('superiorskyblock2', 'SuperiorSkyblock2 Migration Input', false);

CREATE TABLE IF NOT EXISTS player_profiles (
    uuid CHAR(36) PRIMARY KEY,
    last_name VARCHAR(16),
    primary_island_id CHAR(36),
    locale VARCHAR(16) NOT NULL DEFAULT 'ko_kr',
    last_seen_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_player_profiles_last_name_trimmed CHECK (last_name IS NULL OR last_name = trim(last_name)),
    CONSTRAINT chk_player_profiles_last_name_not_blank CHECK (last_name IS NULL OR trim(last_name) <> ''),
    CONSTRAINT chk_player_profiles_locale_trimmed CHECK (locale = trim(locale)),
    CONSTRAINT chk_player_profiles_locale_not_blank CHECK (trim(locale) <> ''),
    CONSTRAINT chk_player_profiles_locale_lowercase CHECK (locale = lower(locale))
);

CREATE TABLE IF NOT EXISTS islands (
    id CHAR(36) PRIMARY KEY,
    owner_uuid CHAR(36) NOT NULL,
    name VARCHAR(32),
    state VARCHAR(32) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    size INTEGER NOT NULL DEFAULT 300,
    level BIGINT NOT NULL DEFAULT 0,
    worth DECIMAL(20, 2) NOT NULL DEFAULT 0,
    public_access BOOLEAN NOT NULL DEFAULT false,
    locked BOOLEAN NOT NULL DEFAULT false,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6),
    active_owner_uuid CHAR(36) GENERATED ALWAYS AS (IF(deleted_at IS NULL, owner_uuid, NULL)) STORED,
    active_name_ci VARCHAR(32) GENERATED ALWAYS AS (IF(deleted_at IS NULL AND name IS NOT NULL AND trim(name) <> '', lower(name), NULL)) STORED,
    CONSTRAINT fk_islands_template_id FOREIGN KEY (template_id) REFERENCES island_templates(id),
    CONSTRAINT chk_islands_template_id_trimmed CHECK (template_id = trim(template_id)),
    CONSTRAINT chk_islands_template_id_lowercase CHECK (template_id = lower(template_id)),
    CONSTRAINT chk_islands_state_known CHECK (state IN (
        'CREATE_REQUESTED',
        'CREATING',
        'INACTIVE_READY',
        'ACTIVATING',
        'RESTORING',
        'ACTIVE',
        'SAVING',
        'DELETE_REQUESTED',
        'DEACTIVATING',
        'BACKUP_BEFORE_DELETE',
        'DELETING',
        'DELETED',
        'ERROR_CREATING',
        'ERROR_ACTIVATING',
        'ERROR_SAVING',
        'QUARANTINED',
        'RECOVERY_REQUIRED'
    )),
    CONSTRAINT chk_islands_size_positive CHECK (size > 0),
    CONSTRAINT chk_islands_level_non_negative CHECK (level >= 0),
    CONSTRAINT chk_islands_worth_non_negative CHECK (worth >= 0),
    CONSTRAINT chk_islands_name_trimmed CHECK (name IS NULL OR name = trim(name)),
    CONSTRAINT chk_islands_name_not_blank CHECK (name IS NULL OR trim(name) <> ''),
    CONSTRAINT chk_islands_deleted_at_state CHECK ((state = 'DELETED' AND deleted_at IS NOT NULL) OR state <> 'DELETED')
);

CREATE UNIQUE INDEX idx_islands_owner_active ON islands(active_owner_uuid);
CREATE UNIQUE INDEX idx_islands_active_name_ci_unique ON islands(active_name_ci);
CREATE INDEX idx_islands_public_visit_candidates ON islands(public_access, locked, deleted_at, id);
CREATE INDEX idx_islands_state_updated ON islands(state, updated_at DESC);

ALTER TABLE player_profiles
    ADD CONSTRAINT fk_player_profiles_primary_island FOREIGN KEY (primary_island_id) REFERENCES islands(id);

CREATE INDEX idx_player_profiles_last_name_ci ON player_profiles(last_name, last_seen_at DESC);
CREATE INDEX idx_player_profiles_primary_island ON player_profiles(primary_island_id);

CREATE TABLE IF NOT EXISTS island_members (
    island_id CHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    owner_island_id CHAR(36) GENERATED ALWAYS AS (IF(role = 'OWNER', island_id, NULL)) STORED,
    PRIMARY KEY (island_id, player_uuid),
    CONSTRAINT fk_island_members_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_members_role_known CHECK (role IN ('OWNER', 'CO_OWNER', 'MODERATOR', 'MEMBER', 'TRUSTED', 'CUSTOM_1', 'CUSTOM_2', 'CUSTOM_3', 'CUSTOM_4', 'CUSTOM_5')),
    CONSTRAINT chk_island_members_joined_at_present CHECK (joined_at IS NOT NULL)
);

CREATE INDEX idx_island_members_player ON island_members(player_uuid);
CREATE UNIQUE INDEX idx_island_members_one_owner ON island_members(owner_island_id);

CREATE TABLE IF NOT EXISTS island_roles (
    island_id CHAR(36) NOT NULL,
    role VARCHAR(32) NOT NULL,
    weight INTEGER NOT NULL,
    display_name VARCHAR(64),
    PRIMARY KEY (island_id, role),
    CONSTRAINT fk_island_roles_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_roles_role_known CHECK (role IN ('CO_OWNER', 'MODERATOR', 'MEMBER', 'TRUSTED', 'CUSTOM_1', 'CUSTOM_2', 'CUSTOM_3', 'CUSTOM_4', 'CUSTOM_5')),
    CONSTRAINT chk_island_roles_display_name_trimmed CHECK (display_name IS NULL OR display_name = trim(display_name)),
    CONSTRAINT chk_island_roles_display_name_not_blank CHECK (display_name IS NULL OR trim(display_name) <> '')
);

CREATE UNIQUE INDEX idx_island_roles_weight_unique ON island_roles(island_id, weight);

CREATE TABLE IF NOT EXISTS island_permissions (
    island_id CHAR(36) NOT NULL,
    role VARCHAR(32) NOT NULL,
    permission_key VARCHAR(64) NOT NULL,
    value BOOLEAN NOT NULL,
    PRIMARY KEY (island_id, role, permission_key),
    CONSTRAINT fk_island_permissions_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_permissions_role_known CHECK (role IN ('OWNER', 'CO_OWNER', 'MODERATOR', 'MEMBER', 'TRUSTED', 'CUSTOM_1', 'CUSTOM_2', 'CUSTOM_3', 'CUSTOM_4', 'CUSTOM_5', 'VISITOR', 'BANNED')),
    CONSTRAINT chk_island_permissions_key_not_blank CHECK (trim(permission_key) <> ''),
    CONSTRAINT chk_island_permissions_key_trimmed CHECK (permission_key = trim(permission_key))
);

CREATE TABLE IF NOT EXISTS island_flags (
    island_id CHAR(36) NOT NULL,
    flag_key VARCHAR(64) NOT NULL,
    flag_value VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, flag_key),
    CONSTRAINT fk_island_flags_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_flags_key_not_blank CHECK (trim(flag_key) <> ''),
    CONSTRAINT chk_island_flags_key_trimmed CHECK (flag_key = trim(flag_key)),
    CONSTRAINT chk_island_flags_value_not_blank CHECK (trim(flag_value) <> ''),
    CONSTRAINT chk_island_flags_value_trimmed CHECK (flag_value = trim(flag_value))
);

CREATE TABLE IF NOT EXISTS island_bans (
    island_id CHAR(36) NOT NULL,
    banned_uuid CHAR(36) NOT NULL,
    actor_uuid CHAR(36),
    reason TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6),
    PRIMARY KEY (island_id, banned_uuid),
    CONSTRAINT fk_island_bans_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_bans_reason_trimmed CHECK (reason IS NULL OR reason = trim(reason)),
    CONSTRAINT chk_island_bans_reason_not_blank CHECK (reason IS NULL OR trim(reason) <> ''),
    CONSTRAINT chk_island_bans_expires_after_created CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX idx_island_bans_active_lookup ON island_bans(island_id, banned_uuid, expires_at);
CREATE INDEX idx_island_bans_expiring ON island_bans(expires_at);

CREATE TABLE IF NOT EXISTS island_invites (
    id CHAR(36) PRIMARY KEY,
    island_id CHAR(36) NOT NULL,
    inviter_uuid CHAR(36) NOT NULL,
    target_uuid CHAR(36) NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    pending_island_id CHAR(36) GENERATED ALWAYS AS (IF(state = 'PENDING', island_id, NULL)) STORED,
    pending_target_uuid CHAR(36) GENERATED ALWAYS AS (IF(state = 'PENDING', target_uuid, NULL)) STORED,
    CONSTRAINT fk_island_invites_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_invites_state_known CHECK (state IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT chk_island_invites_not_self_invite CHECK (inviter_uuid <> target_uuid),
    CONSTRAINT chk_island_invites_expires_after_created CHECK (expires_at > created_at)
);

CREATE INDEX idx_island_invites_target ON island_invites(target_uuid, state);
CREATE UNIQUE INDEX idx_island_invites_one_pending_per_target ON island_invites(pending_island_id, pending_target_uuid);
CREATE INDEX idx_island_invites_island_state ON island_invites(island_id, state, created_at DESC);

CREATE TABLE IF NOT EXISTS island_homes (
    island_id CHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    local_x DOUBLE NOT NULL,
    local_y DOUBLE NOT NULL,
    local_z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_by CHAR(36),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, name),
    CONSTRAINT fk_island_homes_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_homes_name_not_blank CHECK (trim(name) <> ''),
    CONSTRAINT chk_island_homes_name_trimmed CHECK (name = trim(name)),
    CONSTRAINT chk_island_homes_world_not_blank CHECK (trim(world_name) <> ''),
    CONSTRAINT chk_island_homes_world_trimmed CHECK (world_name = trim(world_name)),
    CONSTRAINT chk_island_homes_y_range CHECK (local_y BETWEEN -2048 AND 2048),
    CONSTRAINT chk_island_homes_yaw_range CHECK (yaw >= -360 AND yaw <= 360),
    CONSTRAINT chk_island_homes_pitch_range CHECK (pitch >= -90 AND pitch <= 90)
);

CREATE TABLE IF NOT EXISTS island_warps (
    island_id CHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'default',
    local_x DOUBLE NOT NULL,
    local_y DOUBLE NOT NULL,
    local_z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    public_access BOOLEAN NOT NULL DEFAULT false,
    created_by CHAR(36),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, name),
    CONSTRAINT fk_island_warps_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_warps_name_not_blank CHECK (trim(name) <> ''),
    CONSTRAINT chk_island_warps_name_trimmed CHECK (name = trim(name)),
    CONSTRAINT chk_island_warps_category_not_blank CHECK (trim(category) <> ''),
    CONSTRAINT chk_island_warps_category_trimmed CHECK (category = trim(category)),
    CONSTRAINT chk_island_warps_category_lowercase CHECK (category = lower(category)),
    CONSTRAINT chk_island_warps_y_range CHECK (local_y BETWEEN -2048 AND 2048),
    CONSTRAINT chk_island_warps_yaw_range CHECK (yaw >= -360 AND yaw <= 360),
    CONSTRAINT chk_island_warps_pitch_range CHECK (pitch >= -90 AND pitch <= 90)
);

CREATE INDEX idx_island_warps_public_recent ON island_warps(public_access, created_at DESC);
CREATE INDEX idx_island_warps_public_category_recent ON island_warps(public_access, category, created_at DESC);
CREATE INDEX idx_island_warps_public_island ON island_warps(island_id, public_access, created_at DESC);

CREATE TABLE IF NOT EXISTS island_reviews (
    island_id CHAR(36) NOT NULL,
    reviewer_uuid CHAR(36) NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(280) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, reviewer_uuid),
    CONSTRAINT fk_island_reviews_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_reviews_rating_range CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_island_reviews_comment_trimmed CHECK (comment = trim(comment))
);

CREATE INDEX idx_island_reviews_recent ON island_reviews(island_id, updated_at DESC);
CREATE INDEX idx_island_reviews_rating_recent ON island_reviews(island_id, rating DESC, updated_at DESC);

CREATE TABLE IF NOT EXISTS island_biomes (
    island_id CHAR(36) PRIMARY KEY,
    biome_key VARCHAR(96) NOT NULL,
    updated_by CHAR(36),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_island_biomes_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_biomes_key_not_blank CHECK (trim(biome_key) <> ''),
    CONSTRAINT chk_island_biomes_key_trimmed CHECK (biome_key = trim(biome_key)),
    CONSTRAINT chk_island_biomes_key_lowercase CHECK (biome_key = lower(biome_key))
);

CREATE TABLE IF NOT EXISTS island_runtime (
    island_id CHAR(36) PRIMARY KEY,
    state VARCHAR(32) NOT NULL,
    active_node VARCHAR(64),
    active_world VARCHAR(64),
    cell_x INTEGER,
    cell_z INTEGER,
    lease_owner VARCHAR(64),
    lease_until DATETIME(6),
    fencing_token BIGINT NOT NULL DEFAULT 0,
    activated_at DATETIME(6),
    last_heartbeat DATETIME(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active_placement_key VARCHAR(180) GENERATED ALWAYS AS (IF(state IN ('ACTIVE', 'ACTIVATING', 'RESTORING', 'SAVING', 'DEACTIVATING') AND active_world IS NOT NULL AND cell_x IS NOT NULL AND cell_z IS NOT NULL, concat(active_world, ':', cell_x, ':', cell_z), NULL)) STORED,
    CONSTRAINT fk_island_runtime_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_runtime_active_location CHECK (state NOT IN ('ACTIVE', 'ACTIVATING', 'RESTORING', 'SAVING', 'DEACTIVATING') OR (active_node IS NOT NULL AND trim(active_node) <> '' AND active_world IS NOT NULL AND trim(active_world) <> '' AND cell_x IS NOT NULL AND cell_z IS NOT NULL)),
    CONSTRAINT chk_island_runtime_fencing_token_non_negative CHECK (fencing_token >= 0),
    CONSTRAINT chk_island_runtime_active_fencing_token_positive CHECK (state NOT IN ('ACTIVE', 'ACTIVATING', 'RESTORING', 'SAVING', 'DEACTIVATING') OR fencing_token > 0),
    CONSTRAINT chk_island_runtime_active_node_trimmed CHECK (active_node IS NULL OR active_node = trim(active_node)),
    CONSTRAINT chk_island_runtime_active_world_trimmed CHECK (active_world IS NULL OR active_world = trim(active_world)),
    CONSTRAINT chk_island_runtime_lease_owner_trimmed CHECK (lease_owner IS NULL OR lease_owner = trim(lease_owner)),
    CONSTRAINT chk_island_runtime_lease_owner_not_blank CHECK (lease_owner IS NULL OR trim(lease_owner) <> ''),
    CONSTRAINT chk_island_runtime_lease_until_has_owner CHECK (lease_until IS NULL OR lease_owner IS NOT NULL)
);

CREATE UNIQUE INDEX idx_island_runtime_active_placement ON island_runtime(active_placement_key);
CREATE INDEX idx_island_runtime_active_node_state ON island_runtime(active_node, state, updated_at DESC);

CREATE TABLE IF NOT EXISTS island_snapshots (
    id CHAR(36) PRIMARY KEY,
    island_id CHAR(36) NOT NULL,
    snapshot_no BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    created_by CHAR(36),
    checksum VARCHAR(128),
    size_bytes BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (island_id, snapshot_no),
    CONSTRAINT fk_island_snapshots_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_snapshots_no_positive CHECK (snapshot_no > 0),
    CONSTRAINT chk_island_snapshots_storage_path_not_blank CHECK (trim(storage_path) <> ''),
    CONSTRAINT chk_island_snapshots_storage_path_trimmed CHECK (storage_path = trim(storage_path)),
    CONSTRAINT chk_island_snapshots_reason_not_blank CHECK (trim(reason) <> ''),
    CONSTRAINT chk_island_snapshots_reason_trimmed CHECK (reason = trim(reason)),
    CONSTRAINT chk_island_snapshots_checksum_trimmed CHECK (checksum IS NULL OR checksum = trim(checksum)),
    CONSTRAINT chk_island_snapshots_checksum_not_blank CHECK (checksum IS NULL OR trim(checksum) <> ''),
    CONSTRAINT chk_island_snapshots_size_non_negative CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE INDEX idx_island_snapshots_latest ON island_snapshots(island_id, snapshot_no DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS route_tickets (
    id CHAR(36) PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    island_id CHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_node VARCHAR(64),
    target_world VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6),
    active_player_uuid CHAR(36) GENERATED ALWAYS AS (IF(state IN ('PREPARING', 'READY'), player_uuid, NULL)) STORED,
    CONSTRAINT fk_route_tickets_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_route_tickets_ready_target CHECK (state <> 'READY' OR (target_node IS NOT NULL AND trim(target_node) <> '' AND target_world IS NOT NULL AND trim(target_world) <> '')),
    CONSTRAINT chk_route_tickets_consumed_at CHECK (state <> 'CONSUMED' OR consumed_at IS NOT NULL),
    CONSTRAINT chk_route_tickets_nonce_not_blank CHECK (trim(nonce) <> ''),
    CONSTRAINT chk_route_tickets_action_known CHECK (action IN ('HOME', 'VISIT', 'WARP', 'ADMIN_TELEPORT', 'RETURN_AFTER_MIGRATION')),
    CONSTRAINT chk_route_tickets_state_known CHECK (state IN ('PREPARING', 'READY', 'CONSUMED', 'EXPIRED', 'CANCELLED', 'FAILED')),
    CONSTRAINT chk_route_tickets_target_node_trimmed CHECK (target_node IS NULL OR target_node = trim(target_node)),
    CONSTRAINT chk_route_tickets_target_world_trimmed CHECK (target_world IS NULL OR target_world = trim(target_world)),
    CONSTRAINT chk_route_tickets_nonce_trimmed CHECK (nonce = trim(nonce))
);

CREATE INDEX idx_route_tickets_player_state ON route_tickets(player_uuid, state, expires_at);
CREATE INDEX idx_route_tickets_island_node_state ON route_tickets(island_id, target_node, state, expires_at);
CREATE INDEX idx_route_tickets_expiry ON route_tickets(state, expires_at);
CREATE UNIQUE INDEX idx_route_tickets_one_active_per_player ON route_tickets(active_player_uuid);
CREATE INDEX idx_route_tickets_island_state ON route_tickets(island_id, state, expires_at);
CREATE INDEX idx_route_tickets_target_node_active ON route_tickets(target_node, state, expires_at);

CREATE TABLE IF NOT EXISTS island_jobs (
    id CHAR(36) PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    island_id CHAR(36),
    target_node VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    request_id VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_by VARCHAR(64),
    locked_until DATETIME(6),
    CONSTRAINT fk_island_jobs_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_jobs_type_not_blank CHECK (trim(job_type) <> ''),
    CONSTRAINT chk_island_jobs_type_known CHECK (job_type IN ('CREATE_ISLAND', 'ACTIVATE_ISLAND', 'DEACTIVATE_ISLAND', 'SAVE_ISLAND', 'SNAPSHOT_ISLAND', 'MIGRATE_ISLAND', 'DELETE_ISLAND', 'RESET_ISLAND', 'RESTORE_ISLAND')),
    CONSTRAINT chk_island_jobs_state_not_blank CHECK (trim(state) <> ''),
    CONSTRAINT chk_island_jobs_state_known CHECK (state IN ('PENDING', 'CLAIMED', 'COMPLETED', 'FAILED', 'CANCELED')),
    CONSTRAINT chk_island_jobs_request_id_not_blank CHECK (trim(request_id) <> ''),
    CONSTRAINT chk_island_jobs_request_id_trimmed CHECK (request_id = trim(request_id)),
    CONSTRAINT chk_island_jobs_retry_bounds CHECK (retry_count >= 0 AND max_retries >= 0 AND retry_count <= max_retries),
    CONSTRAINT chk_island_jobs_target_node_trimmed CHECK (target_node IS NULL OR target_node = trim(target_node)),
    CONSTRAINT chk_island_jobs_locked_by_trimmed CHECK (locked_by IS NULL OR locked_by = trim(locked_by)),
    CONSTRAINT chk_island_jobs_locked_by_not_blank CHECK (locked_by IS NULL OR trim(locked_by) <> ''),
    CONSTRAINT chk_island_jobs_claimed_has_lock CHECK (state <> 'CLAIMED' OR (locked_by IS NOT NULL AND locked_until IS NOT NULL)),
    CONSTRAINT chk_island_jobs_lock_requires_claimed CHECK (locked_by IS NULL OR state = 'CLAIMED')
);

CREATE UNIQUE INDEX idx_island_jobs_request_id ON island_jobs(request_id);
CREATE INDEX idx_island_jobs_claim ON island_jobs(target_node, state, priority DESC, created_at);
CREATE INDEX idx_island_jobs_island_state ON island_jobs(island_id, state, updated_at DESC);
CREATE INDEX idx_island_jobs_pending_claim ON island_jobs(target_node, priority DESC, created_at ASC);

CREATE TABLE IF NOT EXISTS server_nodes (
    id VARCHAR(64) PRIMARY KEY,
    pool VARCHAR(64) NOT NULL,
    velocity_server_name VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    soft_player_cap INTEGER NOT NULL,
    hard_player_cap INTEGER NOT NULL,
    reserved_slots INTEGER NOT NULL DEFAULT 0,
    max_active_islands INTEGER NOT NULL,
    players INTEGER NOT NULL DEFAULT 0,
    active_islands INTEGER NOT NULL DEFAULT 0,
    mspt DOUBLE,
    heap_used_mb INTEGER,
    heap_max_mb INTEGER,
    activation_queue INTEGER NOT NULL DEFAULT 0,
    last_heartbeat DATETIME(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    object_storage_available BOOLEAN NOT NULL DEFAULT true,
    supported_templates TEXT NOT NULL,
    node_version VARCHAR(64) NOT NULL DEFAULT '',
    max_activation_queue INTEGER NOT NULL DEFAULT 20,
    chunk_load_pressure DOUBLE NOT NULL DEFAULT 0,
    recent_failure_penalty INTEGER NOT NULL DEFAULT 0,
    velocity_server_name_ci VARCHAR(64) GENERATED ALWAYS AS (lower(velocity_server_name)) STORED,
    CONSTRAINT chk_server_nodes_identity_not_blank CHECK (trim(id) <> '' AND trim(pool) <> '' AND trim(velocity_server_name) <> '' AND trim(state) <> ''),
    CONSTRAINT chk_server_nodes_capacity_non_negative CHECK (soft_player_cap >= 0 AND hard_player_cap >= 0 AND reserved_slots >= 0 AND max_active_islands >= 0 AND players >= 0 AND active_islands >= 0 AND activation_queue >= 0),
    CONSTRAINT chk_server_nodes_capacity_bounds CHECK (hard_player_cap = 0 OR soft_player_cap = 0 OR soft_player_cap <= hard_player_cap),
    CONSTRAINT chk_server_nodes_metrics_non_negative CHECK ((mspt IS NULL OR mspt >= 0) AND (heap_used_mb IS NULL OR heap_used_mb >= 0) AND (heap_max_mb IS NULL OR heap_max_mb >= 0)),
    CONSTRAINT chk_server_nodes_routing_pressure_non_negative CHECK (max_activation_queue >= 0 AND chunk_load_pressure >= 0 AND recent_failure_penalty >= 0),
    CONSTRAINT chk_server_nodes_activation_queue_bounds CHECK (max_activation_queue = 0 OR activation_queue <= max_activation_queue),
    CONSTRAINT chk_server_nodes_node_version_trimmed CHECK (node_version = trim(node_version)),
    CONSTRAINT chk_server_nodes_supported_templates_not_blank CHECK (trim(supported_templates) <> ''),
    CONSTRAINT chk_server_nodes_supported_templates_trimmed CHECK (supported_templates = trim(supported_templates)),
    CONSTRAINT chk_server_nodes_supported_templates_lowercase CHECK (supported_templates = '*' OR supported_templates = lower(supported_templates)),
    CONSTRAINT chk_server_nodes_supported_templates_list_shape CHECK (supported_templates = '*' OR (supported_templates NOT LIKE ',%' AND supported_templates NOT LIKE '%,' AND supported_templates NOT LIKE '%,,%' AND supported_templates NOT LIKE '% %'))
);

CREATE UNIQUE INDEX idx_server_nodes_pool_velocity_server_unique ON server_nodes(pool, velocity_server_name_ci);

CREATE TABLE IF NOT EXISTS audit_logs (
    id CHAR(36) PRIMARY KEY,
    actor_uuid CHAR(36),
    actor_type VARCHAR(32) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_audit_logs_actor_type_not_blank CHECK (trim(actor_type) <> ''),
    CONSTRAINT chk_audit_logs_actor_type_trimmed CHECK (actor_type = trim(actor_type)),
    CONSTRAINT chk_audit_logs_action_not_blank CHECK (trim(action) <> ''),
    CONSTRAINT chk_audit_logs_action_trimmed CHECK (action = trim(action))
);

CREATE INDEX idx_audit_logs_action_created ON audit_logs(action, created_at DESC);

CREATE TABLE IF NOT EXISTS island_bank (
    island_id CHAR(36) PRIMARY KEY,
    balance DECIMAL(20, 2) NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_island_bank_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_bank_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS island_rank_snapshots (
    island_id CHAR(36) PRIMARY KEY,
    level BIGINT NOT NULL,
    worth DECIMAL(20, 2) NOT NULL,
    member_count INTEGER NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_island_rank_snapshots_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_rank_snapshots_level_non_negative CHECK (level >= 0),
    CONSTRAINT chk_island_rank_snapshots_worth_non_negative CHECK (worth >= 0),
    CONSTRAINT chk_island_rank_snapshots_member_count_non_negative CHECK (member_count >= 0)
);

CREATE INDEX idx_island_rank_level ON island_rank_snapshots(level DESC, worth DESC);
CREATE INDEX idx_island_rank_worth ON island_rank_snapshots(worth DESC, level DESC);

CREATE TABLE IF NOT EXISTS block_values (
    material_key VARCHAR(128) PRIMARY KEY,
    worth DECIMAL(20, 2) NOT NULL,
    level_points BIGINT NOT NULL,
    island_limit INTEGER,
    CONSTRAINT chk_block_values_material_key_not_blank CHECK (trim(material_key) <> ''),
    CONSTRAINT chk_block_values_material_key_trimmed CHECK (material_key = trim(material_key)),
    CONSTRAINT chk_block_values_material_key_lowercase CHECK (material_key = lower(material_key)),
    CONSTRAINT chk_block_values_worth_non_negative CHECK (worth >= 0),
    CONSTRAINT chk_block_values_level_points_non_negative CHECK (level_points >= 0),
    CONSTRAINT chk_block_values_island_limit_non_negative CHECK (island_limit IS NULL OR island_limit >= 0)
);

CREATE TABLE IF NOT EXISTS island_block_counts (
    island_id CHAR(36) NOT NULL,
    material_key VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL,
    dirty BOOLEAN NOT NULL DEFAULT false,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, material_key),
    CONSTRAINT fk_island_block_counts_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_block_counts_material_key_not_blank CHECK (trim(material_key) <> ''),
    CONSTRAINT chk_island_block_counts_material_key_trimmed CHECK (material_key = trim(material_key)),
    CONSTRAINT chk_island_block_counts_material_key_lowercase CHECK (material_key = lower(material_key)),
    CONSTRAINT chk_island_block_counts_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX idx_island_block_counts_dirty_updated ON island_block_counts(dirty, updated_at ASC);

CREATE TABLE IF NOT EXISTS island_upgrades (
    island_id CHAR(36) NOT NULL,
    upgrade_key VARCHAR(64) NOT NULL,
    level INTEGER NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, upgrade_key),
    CONSTRAINT fk_island_upgrades_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_upgrades_key_not_blank CHECK (trim(upgrade_key) <> ''),
    CONSTRAINT chk_island_upgrades_key_trimmed CHECK (upgrade_key = trim(upgrade_key)),
    CONSTRAINT chk_island_upgrades_level_non_negative CHECK (level >= 0)
);

CREATE TABLE IF NOT EXISTS island_missions (
    island_id CHAR(36) NOT NULL,
    mission_key VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    goal BIGINT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT false,
    reward VARCHAR(128) NOT NULL,
    updated_by CHAR(36),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, mission_key),
    CONSTRAINT fk_island_missions_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_missions_key_not_blank CHECK (trim(mission_key) <> ''),
    CONSTRAINT chk_island_missions_key_trimmed CHECK (mission_key = trim(mission_key)),
    CONSTRAINT chk_island_missions_kind_not_blank CHECK (trim(kind) <> ''),
    CONSTRAINT chk_island_missions_kind_trimmed CHECK (kind = trim(kind)),
    CONSTRAINT chk_island_missions_title_not_blank CHECK (trim(title) <> ''),
    CONSTRAINT chk_island_missions_title_trimmed CHECK (title = trim(title)),
    CONSTRAINT chk_island_missions_reward_trimmed CHECK (reward = trim(reward)),
    CONSTRAINT chk_island_missions_progress_non_negative CHECK (progress >= 0),
    CONSTRAINT chk_island_missions_goal_positive CHECK (goal > 0),
    CONSTRAINT chk_island_missions_completed_progress CHECK (completed = false OR progress >= goal)
);

CREATE INDEX idx_island_missions_kind_completed ON island_missions(kind, completed, updated_at DESC);

CREATE TABLE IF NOT EXISTS island_limits (
    island_id CHAR(36) NOT NULL,
    limit_key VARCHAR(32) NOT NULL,
    limit_value BIGINT NOT NULL,
    updated_by CHAR(36),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (island_id, limit_key),
    CONSTRAINT fk_island_limits_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_limits_key_not_blank CHECK (trim(limit_key) <> ''),
    CONSTRAINT chk_island_limits_key_trimmed CHECK (limit_key = trim(limit_key)),
    CONSTRAINT chk_island_limits_value_non_negative CHECK (limit_value >= 0)
);

CREATE TABLE IF NOT EXISTS island_logs (
    id CHAR(36) PRIMARY KEY,
    island_id CHAR(36) NOT NULL,
    actor_uuid CHAR(36),
    action VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_island_logs_island FOREIGN KEY (island_id) REFERENCES islands(id),
    CONSTRAINT chk_island_logs_action_not_blank CHECK (trim(action) <> ''),
    CONSTRAINT chk_island_logs_action_trimmed CHECK (action = trim(action))
);

CREATE TABLE IF NOT EXISTS addon_state (
    addon_id VARCHAR(128) NOT NULL,
    state_key VARCHAR(128) NOT NULL,
    state_value TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (addon_id, state_key),
    CONSTRAINT addon_state_addon_id_length CHECK (char_length(addon_id) BETWEEN 1 AND 128),
    CONSTRAINT addon_state_key_length CHECK (char_length(state_key) BETWEEN 1 AND 128),
    CONSTRAINT addon_state_value_length CHECK (char_length(state_value) <= 65535),
    CONSTRAINT addon_state_addon_id_trimmed CHECK (addon_id = trim(addon_id)),
    CONSTRAINT addon_state_key_trimmed CHECK (state_key = trim(state_key)),
    CONSTRAINT addon_state_table_key_shape CHECK (state_key NOT LIKE 'table/%' OR (state_key LIKE 'table/%/%' AND substring_index(substring_index(state_key, '/', 2), '/', -1) <> '' AND substring_index(state_key, '/', -1) <> ''))
);

CREATE TABLE IF NOT EXISTS addon_island_state (
    addon_id VARCHAR(128) NOT NULL,
    island_id CHAR(36) NOT NULL,
    state_key VARCHAR(128) NOT NULL,
    state_value TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (addon_id, island_id, state_key),
    CONSTRAINT fk_addon_island_state_island FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE,
    CONSTRAINT addon_island_state_addon_id_length CHECK (char_length(addon_id) BETWEEN 1 AND 128),
    CONSTRAINT addon_island_state_key_length CHECK (char_length(state_key) BETWEEN 1 AND 128),
    CONSTRAINT addon_island_state_value_length CHECK (char_length(state_value) <= 65535),
    CONSTRAINT addon_island_state_addon_id_trimmed CHECK (addon_id = trim(addon_id)),
    CONSTRAINT addon_island_state_key_trimmed CHECK (state_key = trim(state_key)),
    CONSTRAINT addon_island_state_table_key_shape CHECK (state_key NOT LIKE 'table/%' OR (state_key LIKE 'table/%/%' AND substring_index(substring_index(state_key, '/', 2), '/', -1) <> '' AND substring_index(state_key, '/', -1) <> ''))
);

CREATE TABLE IF NOT EXISTS migration_runs (
    id CHAR(36) PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    scanned_islands INTEGER NOT NULL DEFAULT 0,
    blocking_issues INTEGER NOT NULL DEFAULT 0,
    manifest_path TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_migration_runs_source_not_blank CHECK (trim(source) <> ''),
    CONSTRAINT chk_migration_runs_source_trimmed CHECK (source = trim(source)),
    CONSTRAINT chk_migration_runs_state_known CHECK (state IN ('SCANNED', 'DRY_RUN_FAILED', 'DRY_RUN_PASSED', 'EXTRACT_FAILED', 'EXTRACTED', 'IMPORTING', 'IMPORTED', 'VERIFYING', 'VERIFIED', 'ROLLED_BACK')),
    CONSTRAINT chk_migration_runs_scanned_islands_non_negative CHECK (scanned_islands >= 0),
    CONSTRAINT chk_migration_runs_blocking_issues_non_negative CHECK (blocking_issues >= 0),
    CONSTRAINT chk_migration_runs_manifest_path_trimmed CHECK (manifest_path IS NULL OR manifest_path = trim(manifest_path)),
    CONSTRAINT chk_migration_runs_manifest_path_not_blank CHECK (manifest_path IS NULL OR trim(manifest_path) <> '')
);

CREATE INDEX idx_migration_runs_source_state_updated ON migration_runs(source, state, updated_at DESC);
