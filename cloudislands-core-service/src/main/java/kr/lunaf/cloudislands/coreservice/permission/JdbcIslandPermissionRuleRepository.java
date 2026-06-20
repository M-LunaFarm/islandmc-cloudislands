package kr.lunaf.cloudislands.coreservice.permission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;

public final class JdbcIslandPermissionRuleRepository implements IslandPermissionRuleRepository {
    private final DataSource dataSource;

    public JdbcIslandPermissionRuleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void put(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        putRoleKey(islandId, role.name(), permission, allowed);
    }

    @Override
    public void putRoleKey(UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        String normalizedRoleKey = kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository.normalizeRoleKey(roleKey);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertPermissionSql(connection))) {
            statement.setObject(1, islandId);
            statement.setString(2, normalizedRoleKey);
            statement.setString(3, permission.name());
            statement.setBoolean(4, allowed);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write island permission", exception);
        }
    }

    @Override
    public List<IslandPermissionRuleSnapshot> list(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, role, permission_key, value FROM island_permissions WHERE island_id = ? ORDER BY role, permission_key")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandPermissionRuleSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new IslandPermissionRuleSnapshot(
                        (UUID) rs.getObject("island_id"),
                        rs.getString("role"),
                        IslandPermission.valueOf(rs.getString("permission_key")),
                        rs.getBoolean("value")
                    ));
                }
                return List.copyOf(result);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island permissions", exception);
        }
    }

    @Override
    public void putPlayerOverride(UUID islandId, UUID playerUuid, IslandPermission permission, boolean allowed) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertOverrideSql(connection))) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.setString(3, permission.name());
            statement.setBoolean(4, allowed);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write island permission override", exception);
        }
    }

    @Override
    public List<IslandPermissionOverrideSnapshot> listPlayerOverrides(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, player_uuid, permission_key, value FROM island_permission_overrides WHERE island_id = ? ORDER BY player_uuid, permission_key")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandPermissionOverrideSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new IslandPermissionOverrideSnapshot(
                        (UUID) rs.getObject("island_id"),
                        (UUID) rs.getObject("player_uuid"),
                        IslandPermission.valueOf(rs.getString("permission_key")),
                        rs.getBoolean("value")
                    ));
                }
                return List.copyOf(result);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island permission overrides", exception);
        }
    }

    private String upsertPermissionSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_permissions(island_id, role, permission_key, value) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)";
        }
        return "INSERT INTO island_permissions(island_id, role, permission_key, value) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, role, permission_key) DO UPDATE SET value = EXCLUDED.value";
    }

    private String upsertOverrideSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_permission_overrides(island_id, player_uuid, permission_key, value) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)";
        }
        return "INSERT INTO island_permission_overrides(island_id, player_uuid, permission_key, value) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, player_uuid, permission_key) DO UPDATE SET value = EXCLUDED.value";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return productName.contains("mysql") || productName.contains("mariadb");
    }
}
