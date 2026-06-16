UPDATE island_invites
SET state = 'EXPIRED'
WHERE state = 'PENDING'
  AND id NOT IN (
      SELECT DISTINCT ON (island_id, target_uuid) id
      FROM island_invites
      WHERE state = 'PENDING'
      ORDER BY island_id, target_uuid, created_at DESC
  );

CREATE UNIQUE INDEX IF NOT EXISTS idx_island_invites_one_pending_per_target
ON island_invites(island_id, target_uuid)
WHERE state = 'PENDING';
