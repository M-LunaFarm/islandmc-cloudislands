package kr.lunaf.cloudislands.coreservice.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
