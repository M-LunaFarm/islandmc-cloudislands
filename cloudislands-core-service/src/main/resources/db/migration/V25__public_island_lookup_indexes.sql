CREATE INDEX IF NOT EXISTS idx_islands_public_visit_candidates
ON islands(id)
WHERE public_access = true
  AND locked = false
  AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_island_warps_public_recent
ON island_warps(created_at DESC)
WHERE public_access = true;
