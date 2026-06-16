package kr.lunaf.cloudislands.coreservice.limit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;

public final class JdbcIslandLimitRepository implements IslandLimitRepository {
    private final DataSource dataSource;

    public JdbcIslandLimitRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<IslandLimitSnapshot> list(UUID islandId) {
        seedDefaults(islandId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, limit_key, limit_value, updated_by, updated_at FROM island_limits WHERE island_id = ? ORDER BY limit_key")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandLimitSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(snapshot(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island limits", exception);
        }
    }

    @Override
    public IslandLimitSnapshot set(UUID islandId, String limitKey, long value, UUID updatedBy) {
        String normalizedKey = normalize(limitKey);
        long normalizedValue = Math.max(0L, value);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertLimitSql(connection))) {
            statement.setObject(1, islandId);
            statement.setString(2, normalizedKey);
            statement.setLong(3, normalizedValue);
            statement.setObject(4, updatedBy);
            statement.executeUpdate();
            IslandLimitSnapshot saved = find(connection, islandId, normalizedKey);
            return saved == null ? new IslandLimitSnapshot(islandId, normalizedKey, normalizedValue, updatedBy, Instant.now()) : saved;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set island limit", exception);
        }
    }

    private void seedDefaults(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertDefaultSql(connection))) {
            putDefault(statement, islandId, "SIZE", 100L);
            putDefault(statement, islandId, "MEMBERS", 3L);
            putDefault(statement, islandId, "WARPS", 1L);
            putDefault(statement, islandId, "HOPPER", 50L);
            putDefault(statement, islandId, "SPAWNER", 25L);
            putDefault(statement, islandId, "ENTITY", 200L);
            putDefault(statement, islandId, "REDSTONE", 512L);
            putDefault(statement, islandId, "BANK", 100000L);
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to seed island limits", exception);
        }
    }

    private IslandLimitSnapshot find(Connection connection, UUID islandId, String limitKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT island_id, limit_key, limit_value, updated_by, updated_at FROM island_limits WHERE island_id = ? AND limit_key = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, limitKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? snapshot(rs) : null;
            }
        }
    }

    private String upsertLimitSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_limits(island_id, limit_key, limit_value, updated_by) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE limit_value = VALUES(limit_value), updated_by = VALUES(updated_by), updated_at = CURRENT_TIMESTAMP";
        }
        return "INSERT INTO island_limits(island_id, limit_key, limit_value, updated_by) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, limit_key) DO UPDATE SET limit_value = EXCLUDED.limit_value, updated_by = EXCLUDED.updated_by, updated_at = now()";
    }

    private String insertDefaultSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT IGNORE INTO island_limits(island_id, limit_key, limit_value, updated_by) VALUES (?, ?, ?, ?)";
        }
        return "INSERT INTO island_limits(island_id, limit_key, limit_value, updated_by) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, limit_key) DO NOTHING";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return productName.contains("mysql") || productName.contains("mariadb");
    }

    private void putDefault(PreparedStatement statement, UUID islandId, String limitKey, long value) throws SQLException {
        statement.setObject(1, islandId);
        statement.setString(2, limitKey);
        statement.setLong(3, value);
        statement.setObject(4, new UUID(0L, 0L));
        statement.addBatch();
    }

    private IslandLimitSnapshot snapshot(ResultSet rs) throws SQLException {
        return new IslandLimitSnapshot((UUID) rs.getObject("island_id"), rs.getString("limit_key"), rs.getLong("limit_value"), (UUID) rs.getObject("updated_by"), rs.getTimestamp("updated_at").toInstant());
    }

    private String normalize(String limitKey) {
        return limitKey == null || limitKey.isBlank() ? "HOPPER" : limitKey.toUpperCase();
    }
}
