package kr.lunaf.cloudislands.coreservice.mission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;

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
    public Optional<IslandMissionSnapshot> complete(UUID islandId, UUID actorUuid, String missionKey) {
        ensureDefaults(islandId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_missions SET progress = goal, completed = true, updated_by = ?, updated_at = now() WHERE island_id = ? AND mission_key = ? RETURNING island_id, mission_key, kind, title, progress, goal, completed, reward, updated_at")) {
            statement.setObject(1, actorUuid);
            statement.setObject(2, islandId);
            statement.setString(3, missionKey.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(snapshot(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to complete island mission", exception);
        }
    }

    private void ensureDefaults(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_missions(island_id, mission_key, kind, title, progress, goal, completed, reward) VALUES (?, ?, ?, ?, 0, ?, false, ?) ON CONFLICT (island_id, mission_key) DO NOTHING")) {
            for (MissionDefinition definition : MissionCatalog.all()) {
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
