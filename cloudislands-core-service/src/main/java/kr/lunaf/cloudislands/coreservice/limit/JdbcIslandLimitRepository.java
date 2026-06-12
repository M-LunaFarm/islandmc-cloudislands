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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_limits(island_id, limit_key, limit_value, updated_by) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, limit_key) DO UPDATE SET limit_value = EXCLUDED.limit_value, updated_by = EXCLUDED.updated_by, updated_at = now() RETURNING island_id, limit_key, limit_value, updated_by, updated_at")) {
            statement.setObject(1, islandId);
            statement.setString(2, normalize(limitKey));
            statement.setLong(3, Math.max(0L, value));
            statement.setObject(4, updatedBy);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return snapshot(rs);
                }
                return new IslandLimitSnapshot(islandId, normalize(limitKey), Math.max(0L, value), updatedBy, Instant.now());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set island limit", exception);
        }
    }

    private void seedDefaults(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_limits(island_id, limit_key, limit_value, updated_by) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, limit_key) DO NOTHING")) {
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
