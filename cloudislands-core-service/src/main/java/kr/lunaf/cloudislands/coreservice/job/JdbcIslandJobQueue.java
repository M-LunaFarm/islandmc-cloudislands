package kr.lunaf.cloudislands.coreservice.job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class JdbcIslandJobQueue implements IslandJobQueue {
    private final DataSource dataSource;
    private final Clock clock;
    private final Duration leaseDuration;

    public JdbcIslandJobQueue(DataSource dataSource, Clock clock, Duration leaseDuration) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
    }

    @Override
    public void publish(IslandJob job) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_jobs(id, job_type, island_id, target_node, state, priority, request_id, payload, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, CAST(? AS jsonb), ?, now()) ON CONFLICT (request_id) DO NOTHING")) {
            statement.setObject(1, job.jobId());
            statement.setString(2, job.type().name());
            statement.setObject(3, job.islandId());
            statement.setString(4, job.targetNode());
            statement.setInt(5, job.priority());
            statement.setString(6, job.jobId().toString());
            statement.setString(7, toJson(job.payload()));
            statement.setObject(8, java.sql.Timestamp.from(job.createdAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to publish jdbc island job", exception);
        }
    }

    @Override
    public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        if (supportedTypes.isEmpty() || maxJobs <= 0) {
            return List.of();
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            List<IslandJob> claimed = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM island_jobs WHERE state = 'PENDING' AND (target_node IS NULL OR target_node = '' OR target_node = ?) AND job_type = ANY (?) ORDER BY priority DESC, created_at ASC LIMIT ? FOR UPDATE SKIP LOCKED")) {
                statement.setString(1, nodeId);
                statement.setArray(2, connection.createArrayOf("varchar", supportedTypes.stream().map(Enum::name).toArray(String[]::new)));
                statement.setInt(3, maxJobs);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        IslandJob job = map(rs);
                        markClaimed(connection, job.jobId(), nodeId);
                        claimed.add(job);
                    }
                }
            }
            connection.commit();
            return List.copyOf(claimed);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to claim jdbc island jobs", exception);
        }
    }

    @Override
    public Optional<IslandJob> findClaimed(UUID jobId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM island_jobs WHERE id = ? AND state = 'CLAIMED'")) {
            statement.setObject(1, jobId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read claimed jdbc island job", exception);
        }
    }

    @Override
    public void complete(String nodeId, UUID jobId) {
        updateState(nodeId, jobId, "COMPLETED", null);
    }

    @Override
    public void fail(String nodeId, UUID jobId, String errorMessage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_jobs SET state = CASE WHEN retry_count + 1 < max_retries THEN 'PENDING' ELSE 'FAILED' END, retry_count = retry_count + 1, error_message = ?, locked_by = CASE WHEN retry_count + 1 < max_retries THEN NULL ELSE locked_by END, locked_until = NULL, updated_at = now() WHERE id = ? AND locked_by = ? AND state = 'CLAIMED'")) {
            statement.setString(1, errorMessage == null ? "unknown" : errorMessage);
            statement.setObject(2, jobId);
            statement.setString(3, nodeId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to fail jdbc island job", exception);
        }
    }

    public String recoverPending(String nodeId, long minIdleMillis, int maxJobs) {
        if (maxJobs <= 0) {
            return "[]";
        }
        Instant staleBefore = clock.instant().minusMillis(Math.max(0L, minIdleMillis));
        List<String> recovered = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM island_jobs WHERE state = 'CLAIMED' AND locked_until IS NOT NULL AND locked_until < now() AND updated_at <= ? ORDER BY updated_at ASC LIMIT ? FOR UPDATE SKIP LOCKED")) {
                statement.setObject(1, java.sql.Timestamp.from(staleBefore));
                statement.setInt(2, maxJobs);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        recovered.add(rs.getObject("id").toString());
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE island_jobs SET state = 'PENDING', locked_by = NULL, locked_until = NULL, error_message = NULL, updated_at = now() WHERE id = ?")) {
                for (String jobId : recovered) {
                    statement.setObject(1, UUID.fromString(jobId));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            return "[\"" + String.join("\",\"", recovered) + "\"]";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to recover jdbc island jobs", exception);
        }
    }

    public boolean retry(UUID jobId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_jobs SET state = 'PENDING', locked_by = NULL, locked_until = NULL, error_message = NULL, updated_at = now() WHERE id = ? AND state IN ('FAILED', 'CLAIMED')")) {
            statement.setObject(1, jobId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to retry jdbc island job", exception);
        }
    }

    public boolean cancel(UUID jobId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_jobs SET state = 'CANCELED', locked_until = NULL, updated_at = now() WHERE id = ? AND state IN ('PENDING', 'CLAIMED', 'FAILED')")) {
            statement.setObject(1, jobId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to cancel jdbc island job", exception);
        }
    }

    public Map<String, Long> countsByState() {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (String state : List.of("PENDING", "CLAIMED", "COMPLETED", "FAILED", "CANCELED")) {
            counts.put(state, 0L);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT state, count(*) AS total FROM island_jobs GROUP BY state");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("state"), rs.getLong("total"));
            }
            return Map.copyOf(counts);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count jdbc island jobs", exception);
        }
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder("{\"jobs\":[");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM island_jobs ORDER BY created_at DESC LIMIT 100");
             ResultSet rs = statement.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('{')
                    .append("\"id\":\"").append(rs.getObject("id")).append("\",")
                    .append("\"type\":\"").append(rs.getString("job_type")).append("\",")
                    .append("\"islandId\":\"").append(rs.getObject("island_id")).append("\",")
                    .append("\"targetNode\":\"").append(rs.getString("target_node") == null ? "" : rs.getString("target_node")).append("\",")
                    .append("\"state\":\"").append(rs.getString("state")).append("\",")
                    .append("\"attempts\":").append(rs.getInt("retry_count")).append(',')
                    .append("\"lockedBy\":\"").append(rs.getString("locked_by") == null ? "" : rs.getString("locked_by")).append("\",")
                    .append("\"error\":\"").append(rs.getString("error_message") == null ? "" : rs.getString("error_message").replace("\"", "'")).append("\"")
                    .append('}');
            }
            return builder.append("]}").toString();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to render jdbc island jobs", exception);
        }
    }

    private void markClaimed(Connection connection, UUID jobId, String nodeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE island_jobs SET state = 'CLAIMED', locked_by = ?, locked_until = ?, updated_at = now() WHERE id = ?")) {
            statement.setString(1, nodeId);
            statement.setObject(2, java.sql.Timestamp.from(clock.instant().plus(leaseDuration)));
            statement.setObject(3, jobId);
            statement.executeUpdate();
        }
    }

    private void updateState(String nodeId, UUID jobId, String state, String errorMessage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_jobs SET state = ?, locked_until = NULL, error_message = ?, updated_at = now() WHERE id = ? AND locked_by = ? AND state = 'CLAIMED'")) {
            statement.setString(1, state);
            statement.setString(2, errorMessage);
            statement.setObject(3, jobId);
            statement.setString(4, nodeId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update jdbc island job", exception);
        }
    }

    private IslandJob map(ResultSet rs) throws SQLException {
        return new IslandJob(
            (UUID) rs.getObject("id"),
            IslandJobType.valueOf(rs.getString("job_type")),
            (UUID) rs.getObject("island_id"),
            rs.getString("target_node"),
            rs.getInt("priority"),
            payload(rs.getString("payload")),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private Map<String, String> payload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String pair : trimmed.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                values.put(unquote(pair.substring(0, colon)), unquote(pair.substring(colon + 1)));
            }
        }
        return Map.copyOf(values);
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

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
