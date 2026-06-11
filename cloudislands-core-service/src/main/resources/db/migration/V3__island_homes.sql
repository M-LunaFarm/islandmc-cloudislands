CREATE TABLE island_homes (
    island_id UUID NOT NULL REFERENCES islands(id),
    name VARCHAR(32) NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    local_x DOUBLE PRECISION NOT NULL,
    local_y DOUBLE PRECISION NOT NULL,
    local_z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, name)
);
