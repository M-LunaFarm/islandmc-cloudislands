CREATE INDEX idx_island_bank_ranking
    ON island_bank (balance DESC, updated_at ASC, island_id ASC);
