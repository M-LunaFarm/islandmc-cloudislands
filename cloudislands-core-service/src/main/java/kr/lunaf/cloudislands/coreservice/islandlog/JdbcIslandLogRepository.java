package kr.lunaf.cloudislands.coreservice.islandlog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.common.json.JsonCodec;
import kr.lunaf.cloudislands.common.json.JsonCodecException;

public final class JdbcIslandLogRepository implements IslandLogRepository {
    private final DataSource dataSource;

    public JdbcIslandLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(UUID islandId, UUID actorUuid, String action, Map<String, String> payload) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertLogSql(connection))) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, islandId);
            statement.setObject(3, actorUuid);
            statement.setString(4, action);
            statement.setString(5, json(payload));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write island log", exception);
        }
    }

    @Override
    public List<IslandLogRecord> list(UUID islandId, int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(listLogSql(connection))) {
            statement.setObject(1, islandId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandLogRecord> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new IslandLogRecord(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("island_id"),
                        (UUID) rs.getObject("actor_uuid"),
                        rs.getString("action"),
                        parsePayload(rs.getString("payload")),
                        rs.getTimestamp("created_at").toInstant()
                    ));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island logs", exception);
        }
    }

    static String json(Map<String, String> payload) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<String, String> entry : payload.entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }
        return JsonCodec.write(normalized);
    }

    static Map<String, String> parsePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> decoded = JsonCodec.readObject(raw);
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : decoded.entrySet()) {
                result.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
            return Map.copyOf(result);
        } catch (JsonCodecException exception) {
            return Map.of();
        }
    }

    private String insertLogSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_logs(id, island_id, actor_uuid, action, payload) VALUES (?, ?, ?, ?, ?)";
        }
        return "INSERT INTO island_logs(id, island_id, actor_uuid, action, payload) VALUES (?, ?, ?, ?, ?::jsonb)";
    }

    private String listLogSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "SELECT id, island_id, actor_uuid, action, payload AS payload, created_at FROM island_logs WHERE island_id = ? ORDER BY created_at DESC LIMIT ?";
        }
        return "SELECT id, island_id, actor_uuid, action, payload::text AS payload, created_at FROM island_logs WHERE island_id = ? ORDER BY created_at DESC LIMIT ?";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return productName.contains("mysql") || productName.contains("mariadb");
    }
}
