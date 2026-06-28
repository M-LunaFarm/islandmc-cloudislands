package kr.lunaf.cloudislands.coreservice.generator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;

public final class JdbcIslandGeneratorRepository implements IslandGeneratorRepository {
    private final DataSource dataSource;

    public JdbcIslandGeneratorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public IslandGeneratorSnapshot profile(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, generator_key, level, updated_at FROM island_generator_profiles WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next()
                    ? new IslandGeneratorSnapshot((UUID) rs.getObject("island_id"), rs.getString("generator_key"), rs.getInt("level"), rs.getTimestamp("updated_at").toInstant())
                    : new IslandGeneratorSnapshot(islandId, "default", 1, Instant.EPOCH);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island generator profile", exception);
        }
    }

    @Override
    public IslandGeneratorSnapshot setProfile(UUID islandId, String generatorKey, int level) {
        IslandGeneratorSnapshot normalized = new IslandGeneratorSnapshot(islandId, generatorKey, level, Instant.now());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertProfileSql(connection))) {
            statement.setObject(1, normalized.islandId());
            statement.setString(2, normalized.generatorKey());
            statement.setInt(3, normalized.level());
            statement.executeUpdate();
            return normalized;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save island generator profile", exception);
        }
    }

    @Override
    public List<GeneratorRuleSnapshot> rules(String generatorKey) {
        String key = generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey.trim().toLowerCase();
        List<GeneratorRuleSnapshot> result = readRules(key);
        if (result.isEmpty()) {
            seedDefaults();
            result = readRules(key);
        }
        return result.isEmpty() && !key.equals("default") ? rules("default") : result;
    }

    @Override
    public List<GeneratorRuleSnapshot> setRules(String generatorKey, List<GeneratorRuleSnapshot> rules) {
        String key = generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey.trim().toLowerCase();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM generator_rules WHERE generator_key = ?");
             PreparedStatement insert = connection.prepareStatement(insertRuleSql(connection))) {
            delete.setString(1, key);
            delete.executeUpdate();
            for (GeneratorRuleSnapshot rule : rules == null ? List.<GeneratorRuleSnapshot>of() : rules) {
                bindRule(insert, new GeneratorRuleSnapshot(key, rule.materialKey(), rule.chance(), rule.minIslandLevel(), rule.minUpgradeLevel(), rule.biomeKey(), rule.enabled()));
                insert.addBatch();
            }
            insert.executeBatch();
            return readRules(key);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save generator rules", exception);
        }
    }

    private List<GeneratorRuleSnapshot> readRules(String generatorKey) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT generator_key, material_key, chance, min_island_level, min_upgrade_level, biome_key, enabled FROM generator_rules WHERE generator_key = ? AND enabled = true ORDER BY min_upgrade_level, chance DESC, material_key")) {
            statement.setString(1, generatorKey);
            try (ResultSet rs = statement.executeQuery()) {
                List<GeneratorRuleSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new GeneratorRuleSnapshot(
                        rs.getString("generator_key"),
                        rs.getString("material_key"),
                        rs.getDouble("chance"),
                        rs.getInt("min_island_level"),
                        rs.getInt("min_upgrade_level"),
                        rs.getString("biome_key"),
                        rs.getBoolean("enabled")
                    ));
                }
                return List.copyOf(result);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read generator rules", exception);
        }
    }

    private void seedDefaults() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertIgnoreRuleSql(connection))) {
            for (GeneratorRuleSnapshot rule : DefaultGeneratorRules.all()) {
                bindRule(statement, rule);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to seed default generator rules", exception);
        }
    }

    private static void bindRule(PreparedStatement statement, GeneratorRuleSnapshot rule) throws SQLException {
        statement.setString(1, rule.generatorKey());
        statement.setString(2, rule.materialKey());
        statement.setDouble(3, rule.chance());
        statement.setInt(4, rule.minIslandLevel());
        statement.setInt(5, rule.minUpgradeLevel());
        statement.setString(6, rule.biomeKey());
        statement.setBoolean(7, rule.enabled());
    }

    private String upsertProfileSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_generator_profiles(island_id, generator_key, level) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE generator_key = VALUES(generator_key), level = VALUES(level), updated_at = now()";
        }
        return "INSERT INTO island_generator_profiles(island_id, generator_key, level) VALUES (?, ?, ?) ON CONFLICT (island_id) DO UPDATE SET generator_key = EXCLUDED.generator_key, level = EXCLUDED.level, updated_at = now()";
    }

    private String insertRuleSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO generator_rules(generator_key, material_key, chance, min_island_level, min_upgrade_level, biome_key, enabled) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE chance = VALUES(chance), min_island_level = VALUES(min_island_level), min_upgrade_level = VALUES(min_upgrade_level), biome_key = VALUES(biome_key), enabled = VALUES(enabled), updated_at = now()";
        }
        return "INSERT INTO generator_rules(generator_key, material_key, chance, min_island_level, min_upgrade_level, biome_key, enabled) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (generator_key, material_key, min_island_level, min_upgrade_level, biome_key) DO UPDATE SET chance = EXCLUDED.chance, enabled = EXCLUDED.enabled, updated_at = now()";
    }

    private String insertIgnoreRuleSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT IGNORE INTO generator_rules(generator_key, material_key, chance, min_island_level, min_upgrade_level, biome_key, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)";
        }
        return "INSERT INTO generator_rules(generator_key, material_key, chance, min_island_level, min_upgrade_level, biome_key, enabled) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (generator_key, material_key, min_island_level, min_upgrade_level, biome_key) DO NOTHING";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return productName.contains("mysql") || productName.contains("mariadb");
    }
}
