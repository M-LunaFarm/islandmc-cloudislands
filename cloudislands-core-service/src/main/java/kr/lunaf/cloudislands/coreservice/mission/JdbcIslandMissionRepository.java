package kr.lunaf.cloudislands.coreservice.mission;

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
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;

public final class JdbcIslandMissionRepository implements IslandMissionRepository {
    private final DataSource dataSource;

    public JdbcIslandMissionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<IslandMissionSnapshot> list(UUID islandId, String kind) {
        ensureDefaults(islandId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, mission_key, kind, title, progress, goal, completed, reward, updated_at FROM island_missions WHERE island_id = ? AND kind = ? ORDER BY mission_key")) {
            statement.setObject(1, islandId);
            statement.setString(2, MissionCatalog.normalizeKind(kind));
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandMissionSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(snapshot(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island missions", exception);
        }
    }

    @Override
    public Optional<IslandMissionSnapshot> complete(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        ensureDefaults(islandId);
        String safeKey = missionKey.toLowerCase();
        String safeKind = MissionCatalog.normalizeKind(kind);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_missions SET progress = goal, completed = true, updated_by = ?, updated_at = now() WHERE island_id = ? AND mission_key = ? AND kind = ?")) {
            statement.setObject(1, actorUuid);
            statement.setObject(2, islandId);
            statement.setString(3, safeKey);
            statement.setString(4, safeKind);
            return statement.executeUpdate() > 0 ? find(islandId, safeKey, safeKind) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to complete island mission", exception);
        }
    }

    @Override
    public Optional<IslandMissionSnapshot> progress(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        ensureDefaults(islandId);
        long safeAmount = Math.max(0L, amount);
        String safeKey = missionKey.toLowerCase();
        String safeKind = MissionCatalog.normalizeKind(kind);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_missions SET progress = LEAST(goal, progress + ?), completed = completed OR LEAST(goal, progress + ?) >= goal, updated_by = ?, updated_at = now() WHERE island_id = ? AND mission_key = ? AND kind = ?")) {
            statement.setLong(1, safeAmount);
            statement.setLong(2, safeAmount);
            statement.setObject(3, actorUuid);
            statement.setObject(4, islandId);
            statement.setString(5, safeKey);
            statement.setString(6, safeKind);
            return statement.executeUpdate() > 0 ? find(islandId, safeKey, safeKind) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to progress island mission", exception);
        }
    }

    @Override
    public IslandMissionSnapshot importCompleted(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        ensureDefaults(islandId);
        String key = missionKey.toLowerCase();
        String safeKind = MissionCatalog.normalizeKind(kind);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(importCompletedSql(connection))) {
            statement.setObject(1, islandId);
            statement.setString(2, key);
            statement.setString(3, safeKind);
            statement.setString(4, key);
            statement.setObject(5, actorUuid);
            statement.executeUpdate();
            return find(islandId, key, safeKind).orElseThrow(() -> new IllegalStateException("mission import did not update a row"));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to import completed island mission", exception);
        }
    }

    @Override
    public List<MissionProviderDefinitionSnapshot> listProviderDefinitions(String providerId) {
        try (Connection connection = dataSource.getConnection()) {
            return listProviderDefinitions(connection, providerId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read mission provider definitions", exception);
        }
    }

    @Override
    public List<MissionProviderDefinitionSnapshot> registerProviderDefinitions(String providerId, List<MissionProviderDefinitionSnapshot> definitions) {
        String provider = providerId == null || providerId.isBlank() ? "unknown-provider" : providerId.trim();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(registerProviderDefinitionSql(connection))) {
            for (MissionProviderDefinitionSnapshot definition : definitions == null ? List.<MissionProviderDefinitionSnapshot>of() : definitions) {
                MissionProviderDefinitionSnapshot normalized = new MissionProviderDefinitionSnapshot(provider, definition.missionKey(), definition.kind(), definition.title(), definition.goal(), definition.reward(), definition.enabled(), Instant.now());
                if (normalized.missionKey().isBlank()) {
                    continue;
                }
                statement.setString(1, normalized.providerId());
                statement.setString(2, normalized.missionKey());
                statement.setString(3, normalized.kind());
                statement.setString(4, normalized.title());
                statement.setLong(5, normalized.goal());
                statement.setString(6, normalized.reward());
                statement.setBoolean(7, normalized.enabled());
                statement.addBatch();
            }
            statement.executeBatch();
            return listProviderDefinitions(connection, provider);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to register mission provider definitions", exception);
        }
    }

    private void ensureDefaults(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(ensureDefaultSql(connection))) {
            for (MissionDefinition definition : definitions(connection)) {
                if (!definition.enabled()) {
                    continue;
                }
                statement.setObject(1, islandId);
                statement.setString(2, definition.missionKey());
                statement.setString(3, definition.kind());
                statement.setString(4, definition.title());
                statement.setLong(5, definition.goal());
                statement.setString(6, definition.reward());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to seed island missions", exception);
        }
    }

    private List<MissionDefinition> definitions(Connection connection) throws SQLException {
        List<MissionDefinition> definitions = new ArrayList<>(MissionCatalog.all());
        for (MissionProviderDefinitionSnapshot snapshot : listProviderDefinitions(connection, "")) {
            definitions.add(new MissionDefinition(snapshot));
        }
        return definitions;
    }

    private List<MissionProviderDefinitionSnapshot> listProviderDefinitions(Connection connection, String providerId) throws SQLException {
        String provider = providerId == null || providerId.isBlank() ? "" : providerId.trim();
        String sql = provider.isBlank()
            ? "SELECT provider_id, mission_key, kind, title, goal, reward, enabled, updated_at FROM island_mission_definitions ORDER BY provider_id, mission_key"
            : "SELECT provider_id, mission_key, kind, title, goal, reward, enabled, updated_at FROM island_mission_definitions WHERE provider_id = ? ORDER BY mission_key";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!provider.isBlank()) {
                statement.setString(1, provider);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<MissionProviderDefinitionSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new MissionProviderDefinitionSnapshot(
                        rs.getString("provider_id"),
                        rs.getString("mission_key"),
                        rs.getString("kind"),
                        rs.getString("title"),
                        rs.getLong("goal"),
                        rs.getString("reward"),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("updated_at").toInstant()
                    ));
                }
                return result;
            }
        }
    }

    private Optional<IslandMissionSnapshot> find(UUID islandId, String missionKey, String kind) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, mission_key, kind, title, progress, goal, completed, reward, updated_at FROM island_missions WHERE island_id = ? AND mission_key = ? AND kind = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, missionKey);
            statement.setString(3, kind);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(snapshot(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island mission", exception);
        }
    }

    private String importCompletedSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_missions(island_id, mission_key, kind, title, progress, goal, completed, reward, updated_by) VALUES (?, ?, ?, ?, 1, 1, true, '', ?) ON DUPLICATE KEY UPDATE progress = goal, completed = true, updated_by = VALUES(updated_by), updated_at = now()";
        }
        return "INSERT INTO island_missions(island_id, mission_key, kind, title, progress, goal, completed, reward, updated_by) VALUES (?, ?, ?, ?, 1, 1, true, '', ?) ON CONFLICT (island_id, mission_key) DO UPDATE SET progress = island_missions.goal, completed = true, updated_by = EXCLUDED.updated_by, updated_at = now()";
    }

    private String ensureDefaultSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT IGNORE INTO island_missions(island_id, mission_key, kind, title, progress, goal, completed, reward) VALUES (?, ?, ?, ?, 0, ?, false, ?)";
        }
        return "INSERT INTO island_missions(island_id, mission_key, kind, title, progress, goal, completed, reward) VALUES (?, ?, ?, ?, 0, ?, false, ?) ON CONFLICT (island_id, mission_key) DO NOTHING";
    }

    private String registerProviderDefinitionSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_mission_definitions(provider_id, mission_key, kind, title, goal, reward, enabled) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE provider_id = VALUES(provider_id), kind = VALUES(kind), title = VALUES(title), goal = VALUES(goal), reward = VALUES(reward), enabled = VALUES(enabled), updated_at = now()";
        }
        return "INSERT INTO island_mission_definitions(provider_id, mission_key, kind, title, goal, reward, enabled) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (mission_key) DO UPDATE SET provider_id = EXCLUDED.provider_id, kind = EXCLUDED.kind, title = EXCLUDED.title, goal = EXCLUDED.goal, reward = EXCLUDED.reward, enabled = EXCLUDED.enabled, updated_at = now()";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }

    private IslandMissionSnapshot snapshot(ResultSet rs) throws SQLException {
        return new IslandMissionSnapshot(
            (UUID) rs.getObject("island_id"),
            rs.getString("mission_key"),
            rs.getString("kind"),
            rs.getString("title"),
            rs.getLong("progress"),
            rs.getLong("goal"),
            rs.getBoolean("completed"),
            rs.getString("reward"),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
