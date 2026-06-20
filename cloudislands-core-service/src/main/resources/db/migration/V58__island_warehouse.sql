CREATE TABLE island_warehouse (
    island_id UUID NOT NULL REFERENCES islands(id),
    material_key VARCHAR(96) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, material_key),
    CONSTRAINT chk_island_warehouse_material_not_blank CHECK (trim(material_key) <> ''),
    CONSTRAINT chk_island_warehouse_material_trimmed CHECK (material_key = trim(material_key)),
    CONSTRAINT chk_island_warehouse_material_lowercase CHECK (material_key = lower(material_key)),
    CONSTRAINT chk_island_warehouse_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX idx_island_warehouse_amount
    ON island_warehouse(island_id, amount DESC, material_key);
