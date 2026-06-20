CREATE TABLE player_profiles (
    uuid UUID PRIMARY KEY,
    last_name VARCHAR(16),
    primary_island_id UUID,
    locale VARCHAR(16) NOT NULL DEFAULT 'ko_kr',
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE islands (
    id UUID PRIMARY KEY,
    owner_uuid UUID NOT NULL,
    name VARCHAR(32),
    state VARCHAR(32) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    size INTEGER NOT NULL DEFAULT 300,
    level BIGINT NOT NULL DEFAULT 0,
    worth NUMERIC(20, 2) NOT NULL DEFAULT 0,
    public_access BOOLEAN NOT NULL DEFAULT false,
    locked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_islands_owner_active ON islands(owner_uuid) WHERE deleted_at IS NULL;

CREATE TABLE island_members (
    island_id UUID NOT NULL REFERENCES islands(id),
    player_uuid UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, player_uuid)
);

CREATE INDEX idx_island_members_player ON island_members(player_uuid);

CREATE TABLE island_roles (
    island_id UUID NOT NULL REFERENCES islands(id),
    role VARCHAR(32) NOT NULL,
    weight INTEGER NOT NULL,
    display_name VARCHAR(64),
    PRIMARY KEY (island_id, role)
);

CREATE TABLE island_permissions (
    island_id UUID NOT NULL REFERENCES islands(id),
    role VARCHAR(32) NOT NULL,
    permission_key VARCHAR(64) NOT NULL,
    value BOOLEAN NOT NULL,
    PRIMARY KEY (island_id, role, permission_key)
);

CREATE TABLE island_flags (
    island_id UUID NOT NULL REFERENCES islands(id),
    flag_key VARCHAR(64) NOT NULL,
    flag_value VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, flag_key)
);

CREATE TABLE island_bans (
    island_id UUID NOT NULL REFERENCES islands(id),
    banned_uuid UUID NOT NULL,
    actor_uuid UUID,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    PRIMARY KEY (island_id, banned_uuid)
);

CREATE TABLE island_invites (
    id UUID PRIMARY KEY,
    island_id UUID NOT NULL REFERENCES islands(id),
    inviter_uuid UUID NOT NULL,
    target_uuid UUID NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_island_invites_target ON island_invites(target_uuid, state);

CREATE TABLE island_warps (
    island_id UUID NOT NULL REFERENCES islands(id),
    name VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'default',
    local_x DOUBLE PRECISION NOT NULL,
    local_y DOUBLE PRECISION NOT NULL,
    local_z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    public_access BOOLEAN NOT NULL DEFAULT false,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, name)
);

CREATE TABLE island_reviews (
    island_id UUID NOT NULL REFERENCES islands(id),
    reviewer_uuid UUID NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(280) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, reviewer_uuid)
);

CREATE INDEX idx_island_reviews_recent ON island_reviews(island_id, updated_at DESC);
CREATE INDEX idx_island_reviews_rating_recent ON island_reviews(island_id, rating DESC, updated_at DESC);

CREATE TABLE island_warehouse (
    island_id UUID NOT NULL REFERENCES islands(id),
    material_key VARCHAR(96) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, material_key)
);

CREATE INDEX idx_island_warehouse_amount ON island_warehouse(island_id, amount DESC, material_key);

CREATE TABLE island_runtime (
    island_id UUID PRIMARY KEY REFERENCES islands(id),
    state VARCHAR(32) NOT NULL,
    active_node VARCHAR(64),
    active_world VARCHAR(64),
    cell_x INTEGER,
    cell_z INTEGER,
    lease_owner VARCHAR(64),
    lease_until TIMESTAMPTZ,
    fencing_token BIGINT NOT NULL DEFAULT 0,
    activated_at TIMESTAMPTZ,
    last_heartbeat TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE island_snapshots (
    id UUID PRIMARY KEY,
    island_id UUID NOT NULL REFERENCES islands(id),
    snapshot_no BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    created_by UUID,
    checksum VARCHAR(128),
    size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (island_id, snapshot_no)
);

CREATE TABLE route_tickets (
    id UUID PRIMARY KEY,
    player_uuid UUID NOT NULL,
    island_id UUID NOT NULL REFERENCES islands(id),
    action VARCHAR(32) NOT NULL,
    target_node VARCHAR(64),
    target_world VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_route_tickets_player_state ON route_tickets(player_uuid, state, expires_at);

CREATE TABLE island_jobs (
    id UUID PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    island_id UUID REFERENCES islands(id),
    target_node VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    request_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_by VARCHAR(64),
    locked_until TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_island_jobs_request_id ON island_jobs(request_id);

CREATE TABLE server_nodes (
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
    mspt DOUBLE PRECISION,
    heap_used_mb INTEGER,
    heap_max_mb INTEGER,
    activation_queue INTEGER NOT NULL DEFAULT 0,
    last_heartbeat TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_uuid UUID,
    actor_type VARCHAR(32) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE island_rank_snapshots (
    island_id UUID PRIMARY KEY REFERENCES islands(id),
    level BIGINT NOT NULL,
    worth NUMERIC(20, 2) NOT NULL,
    member_count INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_island_rank_level ON island_rank_snapshots(level DESC, worth DESC);
CREATE INDEX idx_island_rank_worth ON island_rank_snapshots(worth DESC, level DESC);

CREATE TABLE block_values (
    material_key VARCHAR(128) PRIMARY KEY,
    worth NUMERIC(20, 2) NOT NULL,
    level_points BIGINT NOT NULL,
    island_limit INTEGER
);

CREATE TABLE island_block_counts (
    island_id UUID NOT NULL REFERENCES islands(id),
    material_key VARCHAR(128) NOT NULL,
    amount BIGINT NOT NULL,
    dirty BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, material_key)
);

CREATE TABLE island_upgrades (
    island_id UUID NOT NULL REFERENCES islands(id),
    upgrade_key VARCHAR(64) NOT NULL,
    level INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, upgrade_key)
);

CREATE TABLE island_logs (
    id UUID PRIMARY KEY,
    island_id UUID NOT NULL REFERENCES islands(id),
    actor_uuid UUID,
    action VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE migration_runs (
    id UUID PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    scanned_islands INTEGER NOT NULL DEFAULT 0,
    blocking_issues INTEGER NOT NULL DEFAULT 0,
    manifest_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
