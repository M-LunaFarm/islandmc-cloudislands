package kr.lunaf.cloudislands.coreservice.http;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

public final class JdbcCoreIdempotencyStore implements CoreIdempotencyStore {
    private static final Duration RETENTION = Duration.ofDays(7);

    private final DataSource dataSource;
    private final AtomicLong beginCount = new AtomicLong();

    public JdbcCoreIdempotencyStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is required");
        }
        this.dataSource = dataSource;
    }

    @Override
    public BeginResult begin(String key, String requestFingerprint) {
        cleanupOccasionally();
        try (Connection connection = dataSource.getConnection()) {
            if (insertPending(connection, key, requestFingerprint)) {
                return BeginResult.owner();
            }
            return existing(connection, key, requestFingerprint);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to claim Core idempotency key", exception);
        }
    }

    @Override
    public void complete(String key, String requestFingerprint, StoredResponse response) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE core_idempotency SET state = 'COMPLETED', response_status = ?, response_content_type = ?, response_body = ?, updated_at = ? WHERE idempotency_key = ? AND request_fingerprint = ? AND state = 'PENDING'"
             )) {
            statement.setInt(1, response.status());
            statement.setString(2, response.contentType());
            statement.setString(3, response.body());
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setString(5, key);
            statement.setString(6, requestFingerprint);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Core idempotency claim could not be completed");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to complete Core idempotency key", exception);
        }
    }

    private boolean insertPending(Connection connection, String key, String requestFingerprint) throws SQLException {
        String sql = mysqlLike(connection)
            ? "INSERT IGNORE INTO core_idempotency(idempotency_key, request_fingerprint, state) VALUES (?, ?, 'PENDING')"
            : "INSERT INTO core_idempotency(idempotency_key, request_fingerprint, state) VALUES (?, ?, 'PENDING') ON CONFLICT (idempotency_key) DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, requestFingerprint);
            return statement.executeUpdate() == 1;
        }
    }

    private BeginResult existing(Connection connection, String key, String requestFingerprint) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT request_fingerprint, state, response_status, response_content_type, response_body FROM core_idempotency WHERE idempotency_key = ?"
        )) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("idempotency row disappeared after duplicate claim");
                }
                if (!requestFingerprint.equals(result.getString("request_fingerprint"))) {
                    return BeginResult.conflict();
                }
                if (!"COMPLETED".equals(result.getString("state"))) {
                    return BeginResult.inProgress();
                }
                return BeginResult.replay(new StoredResponse(
                    result.getInt("response_status"),
                    result.getString("response_content_type"),
                    result.getString("response_body")
                ));
            }
        }
    }

    private void cleanupOccasionally() {
        if ((beginCount.incrementAndGet() & 255L) != 0L) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM core_idempotency WHERE updated_at < ?")) {
            statement.setTimestamp(1, Timestamp.from(Instant.now().minus(RETENTION)));
            statement.executeUpdate();
        } catch (SQLException exception) {
            // Retention cleanup is best effort. Claiming remains fail-closed below.
        }
    }

    private static boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }
}
