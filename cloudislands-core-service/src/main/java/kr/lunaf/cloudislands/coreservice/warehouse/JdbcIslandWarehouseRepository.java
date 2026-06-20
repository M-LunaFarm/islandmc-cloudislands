package kr.lunaf.cloudislands.coreservice.warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;

public final class JdbcIslandWarehouseRepository implements IslandWarehouseRepository {
    private final DataSource dataSource;

    public JdbcIslandWarehouseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ChangeResult deposit(UUID islandId, String materialKey, long amount) {
        String key = IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey);
        if (amount <= 0L) {
            return new ChangeResult(false, "INVALID_AMOUNT", snapshot(islandId, key));
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(depositSql(connection))) {
                statement.setObject(1, islandId);
                statement.setString(2, key);
                statement.setLong(3, amount);
                statement.setLong(4, amount);
                statement.executeUpdate();
            }
            return new ChangeResult(true, "DEPOSITED", snapshot(islandId, key));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to deposit island warehouse item", exception);
        }
    }

    @Override
    public ChangeResult withdraw(UUID islandId, String materialKey, long amount) {
        String key = IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey);
        if (amount <= 0L) {
            return new ChangeResult(false, "INVALID_AMOUNT", snapshot(islandId, key));
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_warehouse SET amount = amount - ?, updated_at = now() WHERE island_id = ? AND material_key = ? AND amount >= ?")) {
            statement.setLong(1, amount);
            statement.setObject(2, islandId);
            statement.setString(3, key);
            statement.setLong(4, amount);
            int updated = statement.executeUpdate();
            IslandWarehouseItemSnapshot snapshot = snapshot(islandId, key);
            return updated > 0 ? new ChangeResult(true, "WITHDRAWN", snapshot) : new ChangeResult(false, "INSUFFICIENT_ITEMS", snapshot);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to withdraw island warehouse item", exception);
        }
    }

    @Override
    public List<IslandWarehouseItemSnapshot> list(UUID islandId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        List<IslandWarehouseItemSnapshot> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, material_key, amount, updated_at FROM island_warehouse WHERE island_id = ? AND amount > 0 ORDER BY amount DESC, material_key LIMIT ?")) {
            statement.setObject(1, islandId);
            statement.setInt(2, cappedLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(snapshot(rs));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list island warehouse", exception);
        }
    }

    private String depositSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_warehouse(island_id, material_key, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + ?, updated_at = CURRENT_TIMESTAMP(6)";
        }
        return "INSERT INTO island_warehouse(island_id, material_key, amount) VALUES (?, ?, ?) ON CONFLICT (island_id, material_key) DO UPDATE SET amount = island_warehouse.amount + ?, updated_at = now()";
    }

    private IslandWarehouseItemSnapshot snapshot(UUID islandId, String materialKey) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, material_key, amount, updated_at FROM island_warehouse WHERE island_id = ? AND material_key = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, materialKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? snapshot(rs) : new IslandWarehouseItemSnapshot(islandId, materialKey, 0L, Instant.EPOCH);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island warehouse item", exception);
        }
    }

    private IslandWarehouseItemSnapshot snapshot(ResultSet rs) throws SQLException {
        Object islandValue = rs.getObject("island_id");
        UUID islandId = islandValue instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(islandValue));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new IslandWarehouseItemSnapshot(islandId, rs.getString("material_key"), rs.getLong("amount"), updatedAt == null ? Instant.EPOCH : updatedAt.toInstant());
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }
}
