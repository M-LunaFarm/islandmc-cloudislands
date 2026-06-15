package kr.lunaf.cloudislands.coreservice.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcAuditLogger implements AuditLogger {
    private final DataSource dataSource;

    public JdbcAuditLogger(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void log(UUID actorUuid, String actorType, String action, String targetType, String targetId, Map<String, String> payload) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO audit_logs(id, actor_uuid, actor_type, action, target_type, target_id, payload) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, actorUuid.getMostSignificantBits() == 0L && actorUuid.getLeastSignificantBits() == 0L ? null : actorUuid);
            statement.setString(3, actorType);
            statement.setString(4, action);
            statement.setString(5, targetType);
            statement.setString(6, targetId);
            statement.setString(7, toJson(payload));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write audit log", exception);
        }
    }

    @Override
    public String toJson(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, actor_uuid, actor_type, action, target_type, target_id, payload::text AS payload, created_at FROM audit_logs ORDER BY created_at DESC LIMIT ?")) {
            statement.setInt(1, safeLimit);
            StringBuilder builder = new StringBuilder("{\"audit\":[");
            boolean first = true;
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    builder.append('{')
                        .append("\"id\":\"").append(rs.getObject("id")).append("\",")
                        .append("\"actorUuid\":").append(rs.getObject("actor_uuid") == null ? "null" : "\"" + rs.getObject("actor_uuid") + "\"").append(',')
                        .append("\"actorType\":\"").append(escape(rs.getString("actor_type"))).append("\",")
                        .append("\"action\":\"").append(escape(rs.getString("action"))).append("\",")
                        .append("\"targetType\":\"").append(escape(rs.getString("target_type"))).append("\",")
                        .append("\"targetId\":\"").append(escape(rs.getString("target_id"))).append("\",")
                        .append("\"payload\":").append(rs.getString("payload") == null ? "{}" : rs.getString("payload")).append(',')
                        .append("\"createdAt\":\"").append(rs.getTimestamp("created_at").toInstant()).append("\"")
                        .append('}');
                }
            }
            return builder.append("]}").toString();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read audit log", exception);
        }
    }

    private String toJson(Map<String, String> payload) {
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

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
