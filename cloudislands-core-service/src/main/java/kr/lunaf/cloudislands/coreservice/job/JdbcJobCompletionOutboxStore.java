package kr.lunaf.cloudislands.coreservice.job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class JdbcJobCompletionOutboxStore implements JobCompletionOutboxStore {
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private final DataSource dataSource;

    public JdbcJobCompletionOutboxStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(List<JobCompletionEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean mysqlLike = mysqlLike(connection);
            String payloadValue = mysqlLike ? "?" : "CAST(? AS jsonb)";
            String insert = "INSERT INTO core_event_outbox(event_id, aggregate_id, aggregate_version, event_type, payload, status, attempts, next_attempt_at, created_at, updated_at) VALUES (?, ?, ?, ?, " + payloadValue + ", 'PENDING', 0, ?, ?, ?)";
            String sql = mysqlLike
                ? insert.replace("INSERT INTO", "INSERT IGNORE INTO")
                : insert + " ON CONFLICT (event_id) DO NOTHING";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                Timestamp now = Timestamp.from(Instant.now());
                for (JobCompletionEvent event : events) {
                    statement.setObject(1, event.eventId());
                    statement.setObject(2, event.aggregateId());
                    statement.setLong(3, event.aggregateVersion());
                    statement.setString(4, event.eventType());
                    statement.setString(5, fieldsJson(event.fields()));
                    statement.setObject(6, now);
                    statement.setObject(7, now);
                    statement.setObject(8, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to append completion outbox events", exception);
        }
    }

    @Override
    public List<JobCompletionOutboxEntry> claimDue(int limit, Instant now) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Instant safeNow = now == null ? Instant.now() : now;
        try (Connection connection = dataSource.getConnection()) {
            boolean mysqlLike = mysqlLike(connection);
            connection.setAutoCommit(false);
            List<JobCompletionOutboxEntry> entries = readDue(connection, mysqlLike, safeLimit, safeNow);
            markDispatching(connection, entries);
            connection.commit();
            return entries;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to claim completion outbox events", exception);
        }
    }

    @Override
    public void markDispatched(UUID eventId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE core_event_outbox SET status = 'DISPATCHED', dispatched_at = ?, updated_at = ? WHERE event_id = ?")) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setObject(1, now);
            statement.setObject(2, now);
            statement.setObject(3, eventId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark completion outbox event dispatched", exception);
        }
    }

    @Override
    public void markFailed(UUID eventId, String error, Instant nextAttemptAt, boolean terminal) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE core_event_outbox SET status = ?, last_error = ?, next_attempt_at = ?, updated_at = ? WHERE event_id = ?")) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setString(1, terminal ? "FAILED" : "PENDING");
            statement.setString(2, error == null ? "" : error);
            statement.setObject(3, Timestamp.from(nextAttemptAt == null ? Instant.now() : nextAttemptAt));
            statement.setObject(4, now);
            statement.setObject(5, eventId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark completion outbox event failed", exception);
        }
    }

    @Override
    public long pendingCount() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM core_event_outbox WHERE status IN ('PENDING', 'DISPATCHING')");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count completion outbox events", exception);
        }
    }

    private List<JobCompletionOutboxEntry> readDue(Connection connection, boolean mysqlLike, int limit, Instant now) throws SQLException {
        String sql = mysqlLike
            ? "SELECT event_id, aggregate_id, aggregate_version, event_type, payload, attempts FROM core_event_outbox WHERE status IN ('PENDING', 'DISPATCHING') AND next_attempt_at <= ? ORDER BY created_at LIMIT ? FOR UPDATE"
            : "SELECT event_id, aggregate_id, aggregate_version, event_type, payload, attempts FROM core_event_outbox WHERE status IN ('PENDING', 'DISPATCHING') AND next_attempt_at <= ? ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, Timestamp.from(now));
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<JobCompletionOutboxEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(new JobCompletionOutboxEntry(
                        uuid(rs.getObject("event_id")),
                        uuid(rs.getObject("aggregate_id")),
                        rs.getLong("aggregate_version"),
                        rs.getString("event_type"),
                        fieldsFromJson(rs.getString("payload")),
                        rs.getInt("attempts") + 1
                    ));
                }
                return entries;
            }
        }
    }

    private void markDispatching(Connection connection, List<JobCompletionOutboxEntry> entries) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE core_event_outbox SET status = 'DISPATCHING', attempts = attempts + 1, next_attempt_at = ?, updated_at = ? WHERE event_id = ?")) {
            Timestamp now = Timestamp.from(Instant.now());
            Timestamp leaseExpiresAt = Timestamp.from(now.toInstant().plus(CLAIM_LEASE));
            for (JobCompletionOutboxEntry entry : entries) {
                statement.setObject(1, leaseExpiresAt);
                statement.setObject(2, now);
                statement.setObject(3, entry.eventId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String fieldsJson(Map<String, String> fields) {
        return SimpleJson.stringify(new java.util.TreeMap<>(fields == null ? Map.of() : fields));
    }

    private static Map<String, String> fieldsFromJson(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : SimpleJson.object(SimpleJson.parse(json)).entrySet()) {
            fields.put(SimpleJson.text(entry.getKey()), SimpleJson.text(entry.getValue()));
        }
        return Map.copyOf(fields);
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }
}
