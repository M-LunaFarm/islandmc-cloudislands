package kr.lunaf.cloudislands.coreservice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class JdbcNodeRegistry implements NodeRegistry {
    private static final int DEFAULT_HARD_PLAYER_CAP = 110;
    private static final int DEFAULT_MAX_ACTIVE_ISLANDS = 600;
    private static final int DEFAULT_MAX_ACTIVATION_QUEUE = 20;

    private final DataSource dataSource;

    public JdbcNodeRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void heartbeat(NodeHeartbeatRequest request) {
        NodeLoad current = find(request.nodeId()).orElse(null);
        NodeState nextState = current != null && manualLifecycleState(current.state()) ? current.state() : request.state();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO server_nodes(id, pool, velocity_server_name, node_version, state, soft_player_cap, hard_player_cap, reserved_slots, max_active_islands, players, active_islands, mspt, heap_used_mb, heap_max_mb, activation_queue, max_activation_queue, chunk_load_pressure, recent_failure_penalty, object_storage_available, supported_templates, last_heartbeat, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) ON CONFLICT (id) DO UPDATE SET pool = EXCLUDED.pool, velocity_server_name = EXCLUDED.velocity_server_name, node_version = EXCLUDED.node_version, state = EXCLUDED.state, soft_player_cap = EXCLUDED.soft_player_cap, hard_player_cap = EXCLUDED.hard_player_cap, reserved_slots = EXCLUDED.reserved_slots, max_active_islands = EXCLUDED.max_active_islands, players = EXCLUDED.players, active_islands = EXCLUDED.active_islands, mspt = EXCLUDED.mspt, heap_used_mb = EXCLUDED.heap_used_mb, heap_max_mb = EXCLUDED.heap_max_mb, activation_queue = EXCLUDED.activation_queue, max_activation_queue = EXCLUDED.max_activation_queue, chunk_load_pressure = EXCLUDED.chunk_load_pressure, recent_failure_penalty = EXCLUDED.recent_failure_penalty, object_storage_available = EXCLUDED.object_storage_available, supported_templates = EXCLUDED.supported_templates, last_heartbeat = now(), updated_at = now()")) {
            statement.setString(1, request.nodeId());
            statement.setString(2, request.pool() == null || request.pool().isBlank() ? "island" : request.pool());
            statement.setString(3, request.velocityServerName());
            statement.setString(4, request.nodeVersion() == null ? "" : request.nodeVersion());
            statement.setString(5, nextState.name());
            statement.setInt(6, request.softPlayerCap());
            statement.setInt(7, request.hardPlayerCap());
            statement.setInt(8, request.reservedSlots());
            statement.setInt(9, request.maxActiveIslands());
            statement.setInt(10, request.players());
            statement.setInt(11, request.activeIslands());
            statement.setDouble(12, request.mspt());
            statement.setLong(13, request.heapUsedMb());
            statement.setLong(14, request.heapMaxMb());
            statement.setInt(15, request.activationQueue());
            statement.setInt(16, request.maxActivationQueue());
            statement.setDouble(17, request.chunkLoadPressure());
            statement.setInt(18, request.recentFailurePenalty());
            statement.setBoolean(19, request.storageAvailable());
            statement.setString(20, request.supportedTemplates() == null || request.supportedTemplates().isBlank() ? "*" : request.supportedTemplates());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record node heartbeat", exception);
        }
    }

    @Override
    public boolean drain(String nodeId) {
        return setState(nodeId, NodeState.DRAINING);
    }

    @Override
    public boolean shutdownSafe(String nodeId) {
        return setState(nodeId, NodeState.SHUTTING_DOWN);
    }

    @Override
    public boolean undrain(String nodeId) {
        return setState(nodeId, NodeState.READY);
    }

    private boolean manualLifecycleState(NodeState state) {
        return state == NodeState.DRAINING || state == NodeState.SHUTTING_DOWN;
    }

    @Override
    public List<String> markStaleDown(Duration heartbeatTimeout) {
        Instant staleBefore = Instant.now().minus(heartbeatTimeout);
        List<String> down = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM server_nodes WHERE state <> 'DOWN' AND (last_heartbeat IS NULL OR last_heartbeat < ?) FOR UPDATE")) {
                statement.setObject(1, java.sql.Timestamp.from(staleBefore));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        down.add(rs.getString("id"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE server_nodes SET state = 'DOWN', updated_at = now() WHERE id = ?")) {
                for (String nodeId : down) {
                    statement.setString(1, nodeId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            return List.copyOf(down);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark stale nodes down", exception);
        }
    }

    @Override
    public List<NodeLoad> snapshot() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM server_nodes ORDER BY id");
             ResultSet rs = statement.executeQuery()) {
            List<NodeLoad> nodes = new ArrayList<>();
            while (rs.next()) {
                nodes.add(map(rs));
            }
            return List.copyOf(nodes);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list nodes", exception);
        }
    }

    @Override
    public Optional<NodeLoad> find(String nodeId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM server_nodes WHERE id = ?")) {
            statement.setString(1, nodeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read node", exception);
        }
    }

    private boolean setState(String nodeId, NodeState state) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE server_nodes SET state = ?, updated_at = now() WHERE id = ?")) {
            statement.setString(1, state.name());
            statement.setString(2, nodeId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update node state", exception);
        }
    }

    private NodeLoad map(ResultSet rs) throws SQLException {
        java.sql.Timestamp lastHeartbeat = rs.getTimestamp("last_heartbeat");
        return new NodeLoad(
            rs.getString("id"),
            rs.getString("pool") == null ? "island" : rs.getString("pool"),
            rs.getString("velocity_server_name"),
            rs.getString("node_version") == null ? "" : rs.getString("node_version"),
            NodeState.valueOf(rs.getString("state")),
            rs.getInt("players"),
            rs.getInt("soft_player_cap"),
            rs.getInt("hard_player_cap"),
            rs.getInt("reserved_slots"),
            rs.getInt("active_islands"),
            rs.getInt("max_active_islands"),
            rs.getDouble("mspt"),
            rs.getInt("activation_queue"),
            rs.getInt("max_activation_queue"),
            rs.getDouble("chunk_load_pressure"),
            rs.getLong("heap_used_mb"),
            rs.getLong("heap_max_mb"),
            rs.getInt("recent_failure_penalty"),
            lastHeartbeat == null ? Instant.EPOCH : lastHeartbeat.toInstant(),
            rs.getBoolean("object_storage_available"),
            rs.getString("supported_templates") == null ? "*" : rs.getString("supported_templates")
        );
    }
}
