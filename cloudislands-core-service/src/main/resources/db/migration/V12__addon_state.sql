CREATE TABLE addon_state (
    addon_id VARCHAR(128) NOT NULL,
    state_key VARCHAR(128) NOT NULL,
    state_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (addon_id, state_key)
);
