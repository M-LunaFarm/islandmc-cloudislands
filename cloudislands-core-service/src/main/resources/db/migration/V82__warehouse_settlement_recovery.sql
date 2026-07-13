CREATE TABLE warehouse_settlements (
    player_uuid UUID PRIMARY KEY,
    settlement_id UUID NOT NULL UNIQUE,
    island_id UUID NOT NULL REFERENCES islands(id),
    material_key VARCHAR(96) NOT NULL,
    amount BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    owner_node_id VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_warehouse_settlement_amount CHECK (amount > 0),
    CONSTRAINT chk_warehouse_settlement_direction CHECK (direction IN ('DEPOSIT', 'WITHDRAW')),
    CONSTRAINT chk_warehouse_settlement_state CHECK (state IN ('PREPARED', 'ESCROWED')),
    CONSTRAINT chk_warehouse_settlement_material CHECK (trim(material_key) <> ''),
    CONSTRAINT chk_warehouse_settlement_key CHECK (trim(idempotency_key) <> '')
);

CREATE INDEX idx_warehouse_settlements_updated_at
    ON warehouse_settlements(updated_at);
