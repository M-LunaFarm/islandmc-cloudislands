package kr.lunaf.cloudislands.coreservice.upgrade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public final class JdbcIslandUpgradeRepository implements IslandUpgradeRepository {
    private final DataSource dataSource;

    public JdbcIslandUpgradeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<IslandUpgradeSnapshot> find(UUID islandId, String upgradeKey) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, upgrade_key, level, updated_at FROM island_upgrades WHERE island_id = ? AND upgrade_key = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, upgradeKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island upgrade", exception);
        }
    }

    @Override
    public List<IslandUpgradeSnapshot> list(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, upgrade_key, level, updated_at FROM island_upgrades WHERE island_id = ? ORDER BY upgrade_key")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandUpgradeSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list island upgrades", exception);
        }
    }

    @Override
    public IslandUpgradeSnapshot setLevel(UUID islandId, String upgradeKey, UpgradeType type, int level) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertUpgradeSql(connection))) {
            statement.setObject(1, islandId);
            statement.setString(2, upgradeKey);
            statement.setInt(3, level);
            statement.executeUpdate();
            return new IslandUpgradeSnapshot(islandId, upgradeKey, type, level, Instant.now());
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save island upgrade", exception);
        }
    }

    private IslandUpgradeSnapshot map(ResultSet rs) throws SQLException {
        String key = rs.getString("upgrade_key");
        return new IslandUpgradeSnapshot((UUID) rs.getObject("island_id"), key, UpgradePolicy.typeFor(key), rs.getInt("level"), rs.getTimestamp("updated_at").toInstant());
    }

    private String upsertUpgradeSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_upgrades(island_id, upgrade_key, level) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE level = VALUES(level), updated_at = now()";
        }
        return "INSERT INTO island_upgrades(island_id, upgrade_key, level) VALUES (?, ?, ?) ON CONFLICT (island_id, upgrade_key) DO UPDATE SET level = EXCLUDED.level, updated_at = now()";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return productName.contains("mysql") || productName.contains("mariadb");
    }
}
