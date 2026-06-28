package kr.lunaf.cloudislands.coreservice.review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandReviewModerationSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;

public final class JdbcIslandReviewRepository implements IslandReviewRepository {
    private final DataSource dataSource;

    public JdbcIslandReviewRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public IslandReviewSnapshot upsert(UUID islandId, UUID reviewerUuid, int rating, String comment) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
            statement.setObject(1, islandId);
            statement.setObject(2, reviewerUuid);
            statement.setInt(3, IslandReviewSnapshot.normalizeRating(rating));
            statement.setString(4, IslandReviewSnapshot.normalizeComment(comment));
            statement.executeUpdate();
            return find(islandId, reviewerUuid).orElseThrow(() -> new IllegalStateException("island review upsert did not return a row"));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island review", exception);
        }
    }

    @Override
    public Optional<IslandReviewSnapshot> find(UUID islandId, UUID reviewerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, reviewer_uuid, rating, comment, created_at, updated_at FROM island_reviews WHERE island_id = ? AND reviewer_uuid = ?")) {
            statement.setObject(1, islandId);
            statement.setObject(2, reviewerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(snapshot(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island review", exception);
        }
    }

    @Override
    public List<IslandReviewSnapshot> list(UUID islandId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = dataSource.getConnection()) {
            return list(connection, islandId, cappedLimit);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list island reviews", exception);
        }
    }

    private List<IslandReviewSnapshot> list(Connection connection, UUID islandId, int cappedLimit) throws SQLException {
        List<IslandReviewSnapshot> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT island_id, reviewer_uuid, rating, comment, created_at, updated_at FROM island_reviews WHERE island_id = ? AND moderation_state <> 'HIDDEN' ORDER BY updated_at DESC LIMIT ?")) {
            statement.setObject(1, islandId);
            statement.setInt(2, cappedLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(snapshot(rs));
                }
            }
            return rows;
        }
    }

    @Override
    public boolean delete(UUID islandId, UUID reviewerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_reviews WHERE island_id = ? AND reviewer_uuid = ?")) {
            statement.setObject(1, islandId);
            statement.setObject(2, reviewerUuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to delete island review", exception);
        }
    }

    @Override
    public List<IslandReviewRankSnapshot> topByRating(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        List<IslandReviewRankSnapshot> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, AVG(rating) AS average_rating, COUNT(*) AS review_count, MAX(updated_at) AS updated_at FROM island_reviews WHERE moderation_state <> 'HIDDEN' GROUP BY island_id ORDER BY average_rating DESC, review_count DESC, updated_at DESC LIMIT ?")) {
            statement.setInt(1, cappedLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new IslandReviewRankSnapshot(
                        uuid(rs, "island_id"),
                        rs.getDouble("average_rating"),
                        rs.getInt("review_count"),
                        instant(rs, "updated_at")
                    ));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island review rankings", exception);
        }
    }

    @Override
    public Optional<IslandReviewModerationSnapshot> report(UUID islandId, UUID reviewerUuid, UUID reporterUuid, String reason) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(reportSql(connection))) {
            statement.setString(1, IslandReviewModerationSnapshot.normalizeText(reason, 180));
            statement.setObject(2, islandId);
            statement.setObject(3, reviewerUuid);
            if (statement.executeUpdate() <= 0) {
                return Optional.empty();
            }
            return moderation(connection, islandId, reviewerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to report island review", exception);
        }
    }

    @Override
    public Optional<IslandReviewModerationSnapshot> moderate(UUID islandId, UUID reviewerUuid, String moderationState, UUID moderatorUuid, String note) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(moderateSql(connection))) {
            statement.setString(1, IslandReviewModerationSnapshot.normalizeState(moderationState));
            statement.setObject(2, moderatorUuid);
            statement.setString(3, IslandReviewModerationSnapshot.normalizeText(note, 180));
            statement.setObject(4, islandId);
            statement.setObject(5, reviewerUuid);
            if (statement.executeUpdate() <= 0) {
                return Optional.empty();
            }
            return moderation(connection, islandId, reviewerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to moderate island review", exception);
        }
    }

    @Override
    public List<IslandReviewModerationSnapshot> moderationQueue(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        List<IslandReviewModerationSnapshot> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, reviewer_uuid, moderation_state, report_count, report_reason, moderated_by, moderated_at, moderation_note, updated_at FROM island_reviews WHERE moderation_state <> 'VISIBLE' ORDER BY report_count DESC, updated_at DESC LIMIT ?")) {
            statement.setInt(1, cappedLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(moderationSnapshot(rs));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list review moderation queue", exception);
        }
    }

    private String upsertSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_reviews(island_id, reviewer_uuid, rating, comment) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment = VALUES(comment), updated_at = CURRENT_TIMESTAMP(6)";
        }
        return "INSERT INTO island_reviews(island_id, reviewer_uuid, rating, comment) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, reviewer_uuid) DO UPDATE SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, updated_at = now()";
    }

    private String reportSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "UPDATE island_reviews SET report_count = report_count + 1, report_reason = ?, moderation_state = CASE WHEN moderation_state = 'HIDDEN' THEN 'HIDDEN' ELSE 'REPORTED' END, updated_at = CURRENT_TIMESTAMP(6) WHERE island_id = ? AND reviewer_uuid = ?";
        }
        return "UPDATE island_reviews SET report_count = report_count + 1, report_reason = ?, moderation_state = CASE WHEN moderation_state = 'HIDDEN' THEN 'HIDDEN' ELSE 'REPORTED' END, updated_at = now() WHERE island_id = ? AND reviewer_uuid = ?";
    }

    private String moderateSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "UPDATE island_reviews SET moderation_state = ?, moderated_by = ?, moderated_at = CURRENT_TIMESTAMP(6), moderation_note = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE island_id = ? AND reviewer_uuid = ?";
        }
        return "UPDATE island_reviews SET moderation_state = ?, moderated_by = ?, moderated_at = now(), moderation_note = ?, updated_at = now() WHERE island_id = ? AND reviewer_uuid = ?";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }

    private IslandReviewSnapshot snapshot(ResultSet rs) throws SQLException {
        return new IslandReviewSnapshot(
            uuid(rs, "island_id"),
            uuid(rs, "reviewer_uuid"),
            rs.getInt("rating"),
            rs.getString("comment"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private Optional<IslandReviewModerationSnapshot> moderation(Connection connection, UUID islandId, UUID reviewerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT island_id, reviewer_uuid, moderation_state, report_count, report_reason, moderated_by, moderated_at, moderation_note, updated_at FROM island_reviews WHERE island_id = ? AND reviewer_uuid = ?")) {
            statement.setObject(1, islandId);
            statement.setObject(2, reviewerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(moderationSnapshot(rs)) : Optional.empty();
            }
        }
    }

    private IslandReviewModerationSnapshot moderationSnapshot(ResultSet rs) throws SQLException {
        return new IslandReviewModerationSnapshot(
            uuid(rs, "island_id"),
            uuid(rs, "reviewer_uuid"),
            rs.getString("moderation_state"),
            rs.getInt("report_count"),
            rs.getString("report_reason"),
            nullableUuid(rs, "moderated_by"),
            instant(rs, "moderated_at"),
            rs.getString("moderation_note"),
            instant(rs, "updated_at")
        );
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
