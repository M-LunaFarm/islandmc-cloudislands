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

    private String json(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private Map<String, String> parsePayload(String raw) {
        if (raw == null || raw.length() < 2) {
            return Map.of();
        }
        String body = raw.substring(1, raw.length() - 1).trim();
        if (body.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                result.put(unquote(pair.substring(0, colon)), unquote(pair.substring(colon + 1)));
            }
        }
        return Map.copyOf(result);
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
