CREATE UNIQUE INDEX IF NOT EXISTS idx_server_nodes_pool_velocity_server_unique
ON server_nodes(pool, lower(velocity_server_name))
WHERE velocity_server_name IS NOT NULL AND trim(velocity_server_name) <> '';
