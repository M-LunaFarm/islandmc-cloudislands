package kr.lunaf.cloudislands.coreservice.ranking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcRankingRepository implements RankingRepository {
    private final DataSource dataSource;

    public JdbcRankingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void markDirty(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_block_counts SET dirty = true WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark island ranking dirty", exception);
        }
    }

    @Override
    public void save(IslandRankSnapshot snapshot) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_rank_snapshots(island_id, level, worth, member_count, updated_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (island_id) DO UPDATE SET level = EXCLUDED.level, worth = EXCLUDED.worth, member_count = EXCLUDED.member_count, updated_at = EXCLUDED.updated_at")) {
            statement.setObject(1, snapshot.islandId());
            statement.setLong(2, snapshot.level());
            statement.setBigDecimal(3, snapshot.worth());
            statement.setInt(4, snapshot.memberCount());
            statement.setObject(5, snapshot.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save island ranking", exception);
        }
    }

    @Override
    public List<IslandRankSnapshot> topByLevel(int limit) {
        return top("level DESC, worth DESC", limit);
    }

    @Override
    public List<IslandRankSnapshot> topByWorth(int limit) {
        return top("worth DESC, level DESC", limit);
    }

    private List<IslandRankSnapshot> top(String order, int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, level, worth, member_count, updated_at FROM island_rank_snapshots ORDER BY " + order + " LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandRankSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new IslandRankSnapshot((UUID) rs.getObject("island_id"), rs.getLong("level"), rs.getBigDecimal("worth"), rs.getInt("member_count"), rs.getTimestamp("updated_at").toInstant()));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island rankings", exception);
        }
    }
}
