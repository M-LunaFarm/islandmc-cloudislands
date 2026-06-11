CREATE TABLE island_biomes (
    island_id UUID PRIMARY KEY REFERENCES islands(id),
    biome_key VARCHAR(96) NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
