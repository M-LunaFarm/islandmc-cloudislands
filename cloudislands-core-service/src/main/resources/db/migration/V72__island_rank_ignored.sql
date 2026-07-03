ALTER TABLE island_rank_snapshots
    ADD COLUMN IF NOT EXISTS ignored BOOLEAN NOT NULL DEFAULT false;
