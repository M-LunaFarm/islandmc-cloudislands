package kr.lunaf.cloudislands.coreservice.ranking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcIslandLevelRepository implements IslandLevelRepository {
    private final DataSource dataSource;

    public JdbcIslandLevelRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void addBlockDelta(UUID islandId, String materialKey, long delta) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(addBlockDeltaSql(connection))) {
            statement.setObject(1, islandId);
            statement.setString(2, materialKey);
            statement.setLong(3, delta);
            statement.setLong(4, delta);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update island block count", exception);
        }
    }

    @Override
    public void replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM island_block_counts WHERE island_id = ?");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO island_block_counts(island_id, material_key, amount, dirty) VALUES (?, ?, ?, true)")) {
                delete.setObject(1, islandId);
                delete.executeUpdate();
                for (Map.Entry<String, Long> entry : counts.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0L) {
                        continue;
                    }
                    insert.setObject(1, islandId);
                    insert.setString(2, entry.getKey());
                    insert.setLong(3, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to replace island block counts", exception);
        }
    }

    @Override
    public Map<String, Long> blockCounts(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT material_key, amount FROM island_block_counts WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, Long> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.put(rs.getString("material_key"), rs.getLong("amount"));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island block counts", exception);
        }
    }

    @Override
    public Map<String, RankingRecalculationService.BlockValue> blockValues() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT material_key, worth, level_points, island_limit FROM block_values")) {
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, RankingRecalculationService.BlockValue> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.put(rs.getString("material_key"), new RankingRecalculationService.BlockValue(rs.getBigDecimal("worth"), rs.getLong("level_points"), rs.getLong("island_limit")));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read block values", exception);
        }
    }

    @Override
    public void putBlockValue(String materialKey, RankingRecalculationService.BlockValue value) {
        if (materialKey == null || materialKey.isBlank() || value == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(putBlockValueSql(connection))) {
            statement.setString(1, materialKey.trim());
            statement.setBigDecimal(2, value.worth());
            statement.setLong(3, value.levelPoints());
            statement.setLong(4, value.limit());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save block value", exception);
        }
    }

    private String addBlockDeltaSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_block_counts(island_id, material_key, amount, dirty) VALUES (?, ?, GREATEST(0, ?), true) ON DUPLICATE KEY UPDATE amount = GREATEST(0, amount + ?), dirty = true, updated_at = now()";
        }
        return "INSERT INTO island_block_counts(island_id, material_key, amount, dirty) VALUES (?, ?, GREATEST(0, ?), true) ON CONFLICT (island_id, material_key) DO UPDATE SET amount = GREATEST(0, island_block_counts.amount + ?), dirty = true, updated_at = now()";
    }

    private String putBlockValueSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO block_values(material_key, worth, level_points, island_limit) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE worth = VALUES(worth), level_points = VALUES(level_points), island_limit = VALUES(island_limit)";
        }
        return "INSERT INTO block_values(material_key, worth, level_points, island_limit) VALUES (?, ?, ?, ?) ON CONFLICT (material_key) DO UPDATE SET worth = EXCLUDED.worth, level_points = EXCLUDED.level_points, island_limit = EXCLUDED.island_limit";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }
}
