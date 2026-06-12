package kr.lunaf.cloudislands.coreservice.role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;

public final class JdbcIslandRoleRepository implements IslandRoleRepository {
    private final DataSource dataSource;

    public JdbcIslandRoleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public IslandRoleSnapshot upsert(UUID islandId, IslandRole role, int weight, String displayName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_roles(island_id, role, weight, display_name) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, role) DO UPDATE SET weight = EXCLUDED.weight, display_name = EXCLUDED.display_name")) {
            statement.setObject(1, islandId);
            statement.setString(2, role.name());
            statement.setInt(3, weight);
            statement.setString(4, displayName == null ? "" : displayName);
            statement.executeUpdate();
            return new IslandRoleSnapshot(islandId, role, weight, displayName == null ? "" : displayName);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write island role", exception);
        }
    }

    @Override
    public List<IslandRoleSnapshot> list(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, role, weight, display_name FROM island_roles WHERE island_id = ? ORDER BY weight, role")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandRoleSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new IslandRoleSnapshot(
                        (UUID) rs.getObject("island_id"),
                        IslandRole.valueOf(rs.getString("role")),
                        rs.getInt("weight"),
                        rs.getString("display_name") == null ? "" : rs.getString("display_name")
                    ));
                }
                return List.copyOf(result);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island roles", exception);
        }
    }
}
