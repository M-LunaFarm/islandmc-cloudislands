ALTER TABLE island_warps
    ADD COLUMN IF NOT EXISTS world_name VARCHAR(64) DEFAULT '';

UPDATE island_warps AS warp
SET world_name = COALESCE(
    NULLIF(runtime.active_world, ''),
    NULLIF(home.world_name, ''),
    warp.world_name,
    ''
)
FROM islands AS island
LEFT JOIN island_runtime AS runtime
    ON runtime.island_id = island.id
LEFT JOIN LATERAL (
    SELECT world_name
    FROM island_homes
    WHERE island_homes.island_id = island.id
      AND trim(world_name) <> ''
    ORDER BY name
    LIMIT 1
) AS home ON true
WHERE warp.island_id = island.id
  AND (warp.world_name IS NULL OR trim(warp.world_name) = '');

ALTER TABLE island_warps
    ADD CONSTRAINT chk_island_warps_world_trimmed
    CHECK (world_name = trim(world_name));
