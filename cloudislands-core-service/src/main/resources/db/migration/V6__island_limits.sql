CREATE TABLE island_limits (
    island_id UUID NOT NULL REFERENCES islands(id),
    limit_key VARCHAR(32) NOT NULL,
    limit_value BIGINT NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, limit_key)
);
