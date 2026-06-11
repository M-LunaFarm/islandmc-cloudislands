CREATE TABLE island_bank (
    island_id UUID PRIMARY KEY REFERENCES islands(id),
    balance NUMERIC(20, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
