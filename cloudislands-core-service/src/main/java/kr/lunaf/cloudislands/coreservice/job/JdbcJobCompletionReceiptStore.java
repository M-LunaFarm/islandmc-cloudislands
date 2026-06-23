package kr.lunaf.cloudislands.coreservice.job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcJobCompletionReceiptStore implements JobCompletionReceiptStore {
    private final DataSource dataSource;

    public JdbcJobCompletionReceiptStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public RecordResult record(JobCompletionRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            if (insert(connection, request)) {
                return RecordResult.NEW;
            }
            String existingHash = readHash(connection, request.job().jobId());
            if (existingHash == null) {
                throw new IllegalStateException("job completion receipt insert was ignored but no receipt exists");
            }
            return existingHash.equals(request.requestHash()) ? RecordResult.REPLAY : RecordResult.CONFLICT;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record job completion receipt", exception);
        }
    }

    @Override
    public void forget(UUID jobId, String requestHash) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM job_completion_receipts WHERE job_id = ? AND request_hash = ?")) {
            statement.setObject(1, jobId);
            statement.setString(2, requestHash);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to remove job completion receipt", exception);
        }
    }

    private boolean insert(Connection connection, JobCompletionRequest request) throws SQLException {
        boolean mysqlLike = mysqlLike(connection);
        String payloadValue = mysqlLike ? "?" : "CAST(? AS jsonb)";
        String receiptValue = mysqlLike ? "?" : "CAST(? AS jsonb)";
        String insert = "INSERT INTO job_completion_receipts(job_id, receipt_id, job_type, island_id, target_node, claimant_node, claim_token, claim_epoch, request_hash, request_payload, receipt_payload, completion_status, aggregate_version, committed_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, " + payloadValue + ", " + receiptValue + ", 'COMPLETED', 0, ?, ?)";
        String sql = mysqlLike
            ? insert.replace("INSERT INTO", "INSERT IGNORE INTO")
            : insert + " ON CONFLICT (job_id) DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, request.job().jobId());
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, request.job().type().name());
            statement.setObject(4, request.job().islandId());
            statement.setString(5, request.job().targetNode());
            statement.setString(6, claimantNode(request));
            statement.setString(7, claimToken(request));
            statement.setLong(8, Math.max(1L, request.job().claimLease().claimEpoch()));
            statement.setString(9, request.requestHash());
            statement.setString(10, request.requestPayloadJson());
            statement.setString(11, request.receiptPayloadJson());
            Timestamp now = Timestamp.from(Instant.now());
            statement.setObject(12, now);
            statement.setObject(13, now);
            return statement.executeUpdate() > 0;
        }
    }

    private String readHash(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT request_hash FROM job_completion_receipts WHERE job_id = ?")) {
            statement.setObject(1, jobId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString("request_hash") : null;
            }
        }
    }

    private String claimantNode(JobCompletionRequest request) {
        String node = request.job().claimLease().claimedByNode();
        if (node == null || node.isBlank()) {
            node = request.job().targetNode();
        }
        return node == null || node.isBlank() ? "unknown" : node;
    }

    private String claimToken(JobCompletionRequest request) {
        String token = request.job().claimLease().claimToken();
        return token == null || token.isBlank() ? "unclaimed" : token;
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }
}
