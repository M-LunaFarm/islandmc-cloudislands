CREATE TABLE IF NOT EXISTS island_ranking_dirty (
    island_id CHAR(36) PRIMARY KEY,
    marked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_island_ranking_dirty_island FOREIGN KEY (island_id) REFERENCES islands(id) ON DELETE CASCADE
);

INSERT IGNORE INTO island_ranking_dirty(island_id)
SELECT DISTINCT island_id
FROM island_block_counts
WHERE dirty = true;

CREATE INDEX idx_island_ranking_dirty_marked
    ON island_ranking_dirty(marked_at, island_id);
