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
        List<IslandReviewSnapshot> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, reviewer_uuid, rating, comment, created_at, updated_at FROM island_reviews WHERE island_id = ? ORDER BY updated_at DESC LIMIT ?")) {
            statement.setObject(1, islandId);
            statement.setInt(2, cappedLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(snapshot(rs));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list island reviews", exception);
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

    private String upsertSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_reviews(island_id, reviewer_uuid, rating, comment) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment = VALUES(comment), updated_at = CURRENT_TIMESTAMP(6)";
        }
        return "INSERT INTO island_reviews(island_id, reviewer_uuid, rating, comment) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, reviewer_uuid) DO UPDATE SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, updated_at = now()";
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

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
