package kr.lunaf.cloudislands.coreservice.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public final class JdbcIslandMetadataRepository implements IslandMetadataRepository {
    private final DataSource dataSource;

    public JdbcIslandMetadataRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<IslandMemberSnapshot> members(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, player_uuid, role, joined_at FROM island_members WHERE island_id = ? ORDER BY joined_at")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandMemberSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new IslandMemberSnapshot((UUID) rs.getObject("island_id"), (UUID) rs.getObject("player_uuid"), IslandRole.valueOf(rs.getString("role")), rs.getTimestamp("joined_at").toInstant()));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island members", exception);
        }
    }

    @Override
    public void upsertMember(UUID islandId, UUID playerUuid, IslandRole role) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_members(island_id, player_uuid, role) VALUES (?, ?, ?) ON CONFLICT (island_id, player_uuid) DO UPDATE SET role = EXCLUDED.role")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.setString(3, role.name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island member", exception);
        }
    }

    @Override
    public void removeMember(UUID islandId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_members WHERE island_id = ? AND player_uuid = ? AND role <> 'OWNER'")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to remove island member", exception);
        }
    }

    @Override
    public IslandFlagsSnapshot flags(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT flag_key, flag_value FROM island_flags WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                Map<IslandFlag, String> result = new EnumMap<>(IslandFlag.class);
                while (rs.next()) {
                    result.put(IslandFlag.valueOf(rs.getString("flag_key")), rs.getString("flag_value"));
                }
                return new IslandFlagsSnapshot(islandId, Map.copyOf(result));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island flags", exception);
        }
    }

    @Override
    public void setFlag(UUID islandId, IslandFlag flag, String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_flags(island_id, flag_key, flag_value) VALUES (?, ?, ?) ON CONFLICT (island_id, flag_key) DO UPDATE SET flag_value = EXCLUDED.flag_value, updated_at = now()")) {
            statement.setObject(1, islandId);
            statement.setString(2, flag.name());
            statement.setString(3, value);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set island flag", exception);
        }
    }

    @Override
    public List<IslandWarpSnapshot> warps(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, name, local_x, local_y, local_z, yaw, pitch, public_access, created_by, created_at FROM island_warps WHERE island_id = ? ORDER BY name")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandWarpSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    IslandLocation location = new IslandLocation("", rs.getDouble("local_x"), rs.getDouble("local_y"), rs.getDouble("local_z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                    result.add(new IslandWarpSnapshot((UUID) rs.getObject("island_id"), rs.getString("name"), location, rs.getBoolean("public_access"), (UUID) rs.getObject("created_by"), rs.getTimestamp("created_at").toInstant()));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island warps", exception);
        }
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_warps(island_id, name, local_x, local_y, local_z, yaw, pitch, public_access, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (island_id, name) DO UPDATE SET local_x = EXCLUDED.local_x, local_y = EXCLUDED.local_y, local_z = EXCLUDED.local_z, yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch, public_access = EXCLUDED.public_access")) {
            statement.setObject(1, islandId);
            statement.setString(2, name.toLowerCase());
            statement.setDouble(3, location.localX());
            statement.setDouble(4, location.localY());
            statement.setDouble(5, location.localZ());
            statement.setFloat(6, location.yaw());
            statement.setFloat(7, location.pitch());
            statement.setBoolean(8, publicAccess);
            statement.setObject(9, createdBy);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island warp", exception);
        }
    }

    @Override
    public void deleteWarp(UUID islandId, String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_warps WHERE island_id = ? AND name = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, name.toLowerCase());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to delete island warp", exception);
        }
    }

    @Override
    public void setPublicAccess(UUID islandId, boolean publicAccess) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE islands SET public_access = ?, updated_at = now() WHERE id = ?")) {
            statement.setBoolean(1, publicAccess);
            statement.setObject(2, islandId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update island access", exception);
        }
    }
}
