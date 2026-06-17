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
             PreparedStatement statement = connection.prepareStatement(insertAuditSql(connection))) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, actorUuid == null || (actorUuid.getMostSignificantBits() == 0L && actorUuid.getLeastSignificantBits() == 0L) ? null : actorUuid);
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
             PreparedStatement statement = connection.prepareStatement(listAuditSql(connection))) {
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
        for (Map.Entry<String, String> entry : (payload == null ? Map.<String, String>of() : payload).entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private String insertAuditSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO audit_logs(id, actor_uuid, actor_type, action, target_type, target_id, payload) VALUES (?, ?, ?, ?, ?, ?, ?)";
        }
        return "INSERT INTO audit_logs(id, actor_uuid, actor_type, action, target_type, target_id, payload) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";
    }

    private String listAuditSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "SELECT id, actor_uuid, actor_type, action, target_type, target_id, payload AS payload, created_at FROM audit_logs ORDER BY created_at DESC LIMIT ?";
        }
        return "SELECT id, actor_uuid, actor_type, action, target_type, target_id, payload::text AS payload, created_at FROM audit_logs ORDER BY created_at DESC LIMIT ?";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return productName.contains("mysql") || productName.contains("mariadb");
    }
}
