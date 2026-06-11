package kr.lunaf.cloudislands.coreservice.snapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;

public final class JdbcIslandSnapshotRepository implements IslandSnapshotRepository {
    private final DataSource dataSource;

    public JdbcIslandSnapshotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public IslandSnapshotRecord record(UUID islandId, long snapshotNo, String storagePath, String reason, UUID createdBy, String checksum, long sizeBytes) {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_snapshots(id, island_id, snapshot_no, storage_path, reason, created_by, checksum, size_bytes) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (island_id, snapshot_no) DO UPDATE SET storage_path = EXCLUDED.storage_path, reason = EXCLUDED.reason, checksum = EXCLUDED.checksum, size_bytes = EXCLUDED.size_bytes")) {
            statement.setObject(1, id);
            statement.setObject(2, islandId);
            statement.setLong(3, snapshotNo);
            statement.setString(4, storagePath);
            statement.setString(5, reason);
            statement.setObject(6, createdBy);
            statement.setString(7, checksum);
            statement.setLong(8, sizeBytes);
            statement.executeUpdate();
            return find(islandId, snapshotNo).orElse(new IslandSnapshotRecord(id, islandId, snapshotNo, storagePath, reason, createdBy, checksum, sizeBytes, java.time.Instant.now()));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record island snapshot", exception);
        }
    }

    @Override
    public List<IslandSnapshotRecord> list(UUID islandId, int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, island_id, snapshot_no, storage_path, reason, created_by, checksum, size_bytes, created_at FROM island_snapshots WHERE island_id = ? ORDER BY snapshot_no DESC LIMIT ?")) {
            statement.setObject(1, islandId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandSnapshotRecord> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list island snapshots", exception);
        }
    }

    @Override
    public Optional<IslandSnapshotRecord> find(UUID islandId, long snapshotNo) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, island_id, snapshot_no, storage_path, reason, created_by, checksum, size_bytes, created_at FROM island_snapshots WHERE island_id = ? AND snapshot_no = ?")) {
            statement.setObject(1, islandId);
            statement.setLong(2, snapshotNo);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find island snapshot", exception);
        }
    }

    @Override
    public int prune(UUID islandId, int keepLatest) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_snapshots WHERE island_id = ? AND snapshot_no NOT IN (SELECT snapshot_no FROM island_snapshots WHERE island_id = ? ORDER BY snapshot_no DESC LIMIT ?)")) {
            statement.setObject(1, islandId);
            statement.setObject(2, islandId);
            statement.setInt(3, Math.max(0, keepLatest));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to prune island snapshots", exception);
        }
    }

    private IslandSnapshotRecord map(ResultSet rs) throws SQLException {
        return new IslandSnapshotRecord(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("island_id"),
            rs.getLong("snapshot_no"),
            rs.getString("storage_path"),
            rs.getString("reason"),
            (UUID) rs.getObject("created_by"),
            rs.getString("checksum"),
            rs.getLong("size_bytes"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
