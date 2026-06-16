UPDATE route_tickets
SET state = 'EXPIRED'
WHERE state IN ('PREPARING', 'READY')
  AND id NOT IN (
      SELECT DISTINCT ON (player_uuid) id
      FROM route_tickets
      WHERE state IN ('PREPARING', 'READY')
      ORDER BY player_uuid, created_at DESC
  );

CREATE UNIQUE INDEX IF NOT EXISTS idx_route_tickets_one_active_per_player
ON route_tickets(player_uuid)
WHERE state IN ('PREPARING', 'READY');
