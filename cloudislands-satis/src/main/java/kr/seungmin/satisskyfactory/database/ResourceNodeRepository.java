package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ResourceNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class ResourceNodeRepository {
    private final DatabaseService database;

    ResourceNodeRepository(DatabaseService database) {
        this.database = database;
    }

    List<ResourceNode> load(UUID islandUuid) {
        List<ResourceNode> nodes = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM resource_nodes WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new ResourceNode(
                            UUID.fromString(rs.getString("node_id")),
                            islandUuid,
                            rs.getString("node_type"),
                            rs.getString("resource_id"),
                            rs.getDouble("purity"),
                            rs.getLong("remaining"),
                            rs.getLong("max_remaining"),
                            rs.getLong("regen_per_hour"),
                            rs.getInt("required_machine_tier"),
                            new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                            rs.getLong("created_at"),
                            rs.getLong("updated_at")
                    ));
                }
            }
            return nodes;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load resource nodes", exception);
        }
    }

    void save(ResourceNode node) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(saveNodeSql())) {
            bind(statement, node, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save resource node", exception);
        }
    }

    void saveAll(Collection<ResourceNode> nodes) {
        Collection<ResourceNode> safeNodes = nodes == null ? List.of() : nodes;
        if (safeNodes.isEmpty()) {
            return;
        }
        try (Connection connection = database.connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(saveNodeSql())) {
                long now = Instant.now().toEpochMilli();
                for (ResourceNode node : safeNodes) {
                    bind(statement, node, now);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save resource node batch", exception);
        }
    }

    private void bind(PreparedStatement statement, ResourceNode node, long now) throws SQLException {
        if (node.createdAt() <= 0) {
            node.createdAt(now);
        }
        node.updatedAt(now);
        statement.setString(1, node.nodeId().toString());
        statement.setString(2, node.islandUuid().toString());
        statement.setString(3, node.nodeType());
        statement.setString(4, node.resourceId());
        statement.setDouble(5, node.purity());
        statement.setLong(6, node.remaining());
        statement.setLong(7, node.maxRemaining());
        statement.setLong(8, node.regenPerHour());
        statement.setInt(9, node.requiredMachineTier());
        statement.setString(10, node.location().world());
        statement.setInt(11, node.location().x());
        statement.setInt(12, node.location().y());
        statement.setInt(13, node.location().z());
        statement.setLong(14, node.createdAt());
        statement.setLong(15, node.updatedAt());
    }

    private String saveNodeSql() {
        if (database.usesMysqlDialect()) {
            return """
                     INSERT INTO resource_nodes(node_id, island_uuid, node_type, resource_id, purity, remaining, max_remaining,
                       regen_per_hour, required_machine_tier, world, x, y, z, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE remaining=VALUES(remaining), world=VALUES(world),
                       x=VALUES(x), y=VALUES(y), z=VALUES(z), updated_at=VALUES(updated_at)
                    """;
        }
        return """
                     INSERT INTO resource_nodes(node_id, island_uuid, node_type, resource_id, purity, remaining, max_remaining,
                       regen_per_hour, required_machine_tier, world, x, y, z, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(node_id) DO UPDATE SET remaining=excluded.remaining, world=excluded.world,
                       x=excluded.x, y=excluded.y, z=excluded.z, updated_at=excluded.updated_at
                    """;
    }
}
