CREATE TABLE addon_island_state (
    addon_id VARCHAR(128) NOT NULL,
    island_id UUID NOT NULL REFERENCES islands(id) ON DELETE CASCADE,
    state_key VARCHAR(128) NOT NULL,
    state_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (addon_id, island_id, state_key),
    CONSTRAINT addon_island_state_addon_id_length CHECK (char_length(addon_id) BETWEEN 1 AND 128),
    CONSTRAINT addon_island_state_key_length CHECK (char_length(state_key) BETWEEN 1 AND 128),
    CONSTRAINT addon_island_state_value_length CHECK (char_length(state_value) <= 4096)
);
