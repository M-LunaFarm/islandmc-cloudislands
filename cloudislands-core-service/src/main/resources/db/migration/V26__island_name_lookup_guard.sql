CREATE UNIQUE INDEX IF NOT EXISTS idx_islands_active_name_ci_unique
ON islands(lower(name))
WHERE name IS NOT NULL
  AND trim(name) <> ''
  AND deleted_at IS NULL;
