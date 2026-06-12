package kr.lunaf.cloudislands.coreservice.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;

public final class JdbcIslandRuntimeRepository implements IslandRuntimeRepository {
    private final DataSource dataSource;

    public JdbcIslandRuntimeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<IslandRuntimeSnapshot> find(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM island_runtime WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island runtime", exception);
        }
    }

    @Override
    public List<IslandRuntimeSnapshot> listByNode(String nodeId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM island_runtime WHERE active_node = ? AND state IN ('ACTIVE', 'ACTIVATING', 'SAVING', 'DEACTIVATING') ORDER BY updated_at DESC LIMIT ?")) {
            statement.setString(1, nodeId);
            statement.setInt(2, cappedLimit);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandRuntimeSnapshot> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(map(rs));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list node island runtimes", exception);
        }
    }

    @Override
    public IslandRuntimeSnapshot markActivating(UUID islandId, String targetNode, String targetWorld, int cellX, int cellZ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long token = lockAndNextToken(connection, islandId);
            IslandRuntimeSnapshot runtime = upsert(connection, islandId, IslandState.ACTIVATING, targetNode, targetWorld, cellX, cellZ, targetNode, token, null);
            connection.commit();
            return runtime;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark island activating", exception);
        }
    }

    @Override
    public IslandRuntimeSnapshot markActive(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, long fencingToken) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            Optional<IslandRuntimeSnapshot> current = findForUpdate(connection, islandId);
            if (current.isPresent() && current.get().fencingToken() > fencingToken) {
                connection.commit();
                return current.get();
            }
            IslandRuntimeSnapshot runtime = upsert(connection, islandId, IslandState.ACTIVE, nodeId, worldName, cellX, cellZ, nodeId, fencingToken, Instant.now());
            connection.commit();
            return runtime;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark island active", exception);
        }
    }

    @Override
    public IslandRuntimeSnapshot markSaving(UUID islandId) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return update(islandId, IslandState.SAVING, current.activeNode(), current.activeWorld(), current.cellX(), current.cellZ(), current.leaseOwner(), current.fencingToken(), current.activatedAt());
    }

    @Override
    public IslandRuntimeSnapshot markInactive(UUID islandId) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return update(islandId, IslandState.INACTIVE_READY, null, null, null, null, null, current.fencingToken(), null);
    }

    @Override
    public IslandRuntimeSnapshot markMigrating(UUID islandId, String targetNode) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long token = lockAndNextToken(connection, islandId);
            IslandRuntimeSnapshot current = findForUpdate(connection, islandId).orElse(defaultRuntime(islandId));
            IslandRuntimeSnapshot runtime = upsert(connection, islandId, IslandState.DEACTIVATING, targetNode, current.activeWorld(), current.cellX(), current.cellZ(), targetNode, token, current.activatedAt());
            connection.commit();
            return runtime;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark island migrating", exception);
        }
    }

    @Override
    public IslandRuntimeSnapshot markQuarantined(UUID islandId, String reason) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return update(islandId, IslandState.QUARANTINED, current.activeNode(), current.activeWorld(), current.cellX(), current.cellZ(), current.leaseOwner(), current.fencingToken(), current.activatedAt());
    }

    @Override
    public IslandRuntimeSnapshot setState(UUID islandId, IslandState state) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return update(islandId, state, current.activeNode(), current.activeWorld(), current.cellX(), current.cellZ(), current.leaseOwner(), current.fencingToken(), current.activatedAt());
    }

    @Override
    public Map<String, Long> countsByState() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (IslandState state : IslandState.values()) {
            counts.put(state.name(), 0L);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT state, count(*) AS total FROM island_runtime GROUP BY state");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("state"), rs.getLong("total"));
            }
            return counts;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count island runtimes by state", exception);
        }
    }

    @Override
    public int markRecoveryRequiredForNode(String nodeId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_runtime SET state = 'RECOVERY_REQUIRED', updated_at = now() WHERE active_node = ? AND state IN ('ACTIVE', 'ACTIVATING', 'SAVING', 'DEACTIVATING')")) {
            statement.setString(1, nodeId);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark node islands recovery required", exception);
        }
    }

    private IslandRuntimeSnapshot update(UUID islandId, IslandState state, String node, String world, Integer cellX, Integer cellZ, String leaseOwner, long token, Instant activatedAt) {
        try (Connection connection = dataSource.getConnection()) {
            return upsert(connection, islandId, state, node, world, cellX, cellZ, leaseOwner, token, activatedAt);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update island runtime", exception);
        }
    }

    private long lockAndNextToken(Connection connection, UUID islandId) throws SQLException {
        Optional<IslandRuntimeSnapshot> current = findForUpdate(connection, islandId);
        return current.map(IslandRuntimeSnapshot::fencingToken).orElse(0L) + 1L;
    }

    private Optional<IslandRuntimeSnapshot> findForUpdate(Connection connection, UUID islandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM island_runtime WHERE island_id = ? FOR UPDATE")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private IslandRuntimeSnapshot upsert(Connection connection, UUID islandId, IslandState state, String node, String world, Integer cellX, Integer cellZ, String leaseOwner, long token, Instant activatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO island_runtime(island_id, state, active_node, active_world, cell_x, cell_z, lease_owner, fencing_token, activated_at, last_heartbeat) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now()) ON CONFLICT (island_id) DO UPDATE SET state = EXCLUDED.state, active_node = EXCLUDED.active_node, active_world = EXCLUDED.active_world, cell_x = EXCLUDED.cell_x, cell_z = EXCLUDED.cell_z, lease_owner = EXCLUDED.lease_owner, fencing_token = EXCLUDED.fencing_token, activated_at = EXCLUDED.activated_at, last_heartbeat = now(), updated_at = now()")) {
            statement.setObject(1, islandId);
            statement.setString(2, state.name());
            statement.setString(3, node);
            statement.setString(4, world);
            setInteger(statement, 5, cellX);
            setInteger(statement, 6, cellZ);
            statement.setString(7, leaseOwner);
            statement.setLong(8, token);
            statement.setObject(9, activatedAt == null ? null : java.sql.Timestamp.from(activatedAt));
            statement.executeUpdate();
        }
        return new IslandRuntimeSnapshot(islandId, state, node, world, cellX, cellZ, leaseOwner, token, activatedAt, Instant.now());
    }

    private void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private IslandRuntimeSnapshot map(ResultSet rs) throws SQLException {
        java.sql.Timestamp activatedAt = rs.getTimestamp("activated_at");
        java.sql.Timestamp lastHeartbeat = rs.getTimestamp("last_heartbeat");
        return new IslandRuntimeSnapshot(
            (UUID) rs.getObject("island_id"),
            IslandState.valueOf(rs.getString("state")),
            rs.getString("active_node"),
            rs.getString("active_world"),
            (Integer) rs.getObject("cell_x"),
            (Integer) rs.getObject("cell_z"),
            rs.getString("lease_owner"),
            rs.getLong("fencing_token"),
            activatedAt == null ? null : activatedAt.toInstant(),
            lastHeartbeat == null ? null : lastHeartbeat.toInstant()
        );
    }

    private IslandRuntimeSnapshot defaultRuntime(UUID islandId) {
        return new IslandRuntimeSnapshot(islandId, IslandState.INACTIVE_READY, null, null, null, null, null, 0L, null, Instant.now());
    }
}
