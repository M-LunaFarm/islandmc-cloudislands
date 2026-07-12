CREATE TABLE IF NOT EXISTS island_ranking_dirty (
    island_id UUID PRIMARY KEY,
    marked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_island_ranking_dirty_island FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE
);

INSERT INTO island_ranking_dirty(island_id)
SELECT DISTINCT island_id
FROM island_block_counts
WHERE dirty = true
ON CONFLICT (island_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_island_ranking_dirty_marked
    ON island_ranking_dirty(marked_at, island_id);
