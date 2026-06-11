CREATE TABLE island_missions (
    island_id UUID NOT NULL REFERENCES islands(id),
    mission_key VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    goal BIGINT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT false,
    reward VARCHAR(128) NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, mission_key)
);
