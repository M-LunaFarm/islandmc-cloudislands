package kr.lunaf.cloudislands.coreservice.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
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
    public boolean isMember(UUID islandId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM island_members WHERE island_id = ? AND player_uuid = ?")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to check island membership", exception);
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
    public IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid) {
        UUID inviteId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(86400);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_invites(id, island_id, inviter_uuid, target_uuid, state, expires_at) VALUES (?, ?, ?, ?, 'PENDING', ?)")) {
            statement.setObject(1, inviteId);
            statement.setObject(2, islandId);
            statement.setObject(3, inviterUuid);
            statement.setObject(4, targetUuid);
            statement.setObject(5, expiresAt);
            statement.executeUpdate();
            return new IslandInviteSnapshot(inviteId, islandId, inviterUuid, targetUuid, "PENDING", Instant.now(), expiresAt);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to create island invite", exception);
        }
    }

    @Override
    public List<IslandInviteSnapshot> pendingInvites(UUID targetUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, island_id, inviter_uuid, target_uuid, state, created_at, expires_at FROM island_invites WHERE target_uuid = ? AND state = 'PENDING' AND expires_at > now() ORDER BY created_at")) {
            statement.setObject(1, targetUuid);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandInviteSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(invite(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island invites", exception);
        }
    }

    @Override
    public boolean acceptInvite(UUID inviteId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            IslandInviteSnapshot invite = lockInvite(connection, inviteId);
            if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING") || !invite.expiresAt().isAfter(Instant.now())) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE island_invites SET state = 'ACCEPTED' WHERE id = ?");
                 PreparedStatement member = connection.prepareStatement("INSERT INTO island_members(island_id, player_uuid, role) VALUES (?, ?, 'MEMBER') ON CONFLICT (island_id, player_uuid) DO UPDATE SET role = EXCLUDED.role")) {
                update.setObject(1, inviteId);
                update.executeUpdate();
                member.setObject(1, invite.islandId());
                member.setObject(2, playerUuid);
                member.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to accept island invite", exception);
        }
    }

    @Override
    public boolean declineInvite(UUID inviteId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            IslandInviteSnapshot invite = lockInvite(connection, inviteId);
            if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING")) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE island_invites SET state = 'DECLINED' WHERE id = ?")) {
                statement.setObject(1, inviteId);
                statement.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to decline island invite", exception);
        }
    }

    @Override
    public boolean isBanned(UUID islandId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM island_bans WHERE island_id = ? AND banned_uuid = ? AND (expires_at IS NULL OR expires_at > now())")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to check island ban", exception);
        }
    }

    @Override
    public List<IslandBanSnapshot> bans(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, banned_uuid, actor_uuid, reason, created_at, expires_at FROM island_bans WHERE island_id = ? AND (expires_at IS NULL OR expires_at > now()) ORDER BY created_at DESC")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandBanSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new IslandBanSnapshot(
                        (UUID) rs.getObject("island_id"),
                        (UUID) rs.getObject("banned_uuid"),
                        (UUID) rs.getObject("actor_uuid"),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()
                    ));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island bans", exception);
        }
    }

    @Override
    public void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_bans(island_id, banned_uuid, actor_uuid, reason) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, banned_uuid) DO UPDATE SET actor_uuid = EXCLUDED.actor_uuid, reason = EXCLUDED.reason, created_at = now(), expires_at = NULL")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.setObject(3, actorUuid);
            statement.setString(4, reason);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to ban island visitor", exception);
        }
    }

    @Override
    public void pardonVisitor(UUID islandId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_bans WHERE island_id = ? AND banned_uuid = ?")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to pardon island visitor", exception);
        }
    }

    @Override
    public boolean isLocked(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT locked FROM islands WHERE id = ? AND deleted_at IS NULL")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean("locked");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island lock state", exception);
        }
    }

    @Override
    public void setLocked(UUID islandId, boolean locked) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE islands SET locked = ?, updated_at = now() WHERE id = ?")) {
            statement.setBoolean(1, locked);
            statement.setObject(2, islandId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update island lock state", exception);
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
    public IslandBiomeSnapshot biome(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, biome_key, updated_by, updated_at FROM island_biomes WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new IslandBiomeSnapshot((UUID) rs.getObject("island_id"), rs.getString("biome_key"), (UUID) rs.getObject("updated_by"), rs.getTimestamp("updated_at").toInstant());
                }
                return new IslandBiomeSnapshot(islandId, "minecraft:plains", new UUID(0L, 0L), Instant.EPOCH);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island biome", exception);
        }
    }

    @Override
    public void setBiome(UUID islandId, String biomeKey, UUID updatedBy) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_biomes(island_id, biome_key, updated_by) VALUES (?, ?, ?) ON CONFLICT (island_id) DO UPDATE SET biome_key = EXCLUDED.biome_key, updated_by = EXCLUDED.updated_by, updated_at = now()")) {
            statement.setObject(1, islandId);
            statement.setString(2, biomeKey);
            statement.setObject(3, updatedBy);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set island biome", exception);
        }
    }

    @Override
    public List<IslandHomeSnapshot> homes(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, name, world_name, local_x, local_y, local_z, yaw, pitch, created_by, created_at FROM island_homes WHERE island_id = ? ORDER BY name")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandHomeSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(home(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island homes", exception);
        }
    }

    @Override
    public java.util.Optional<IslandHomeSnapshot> home(UUID islandId, String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, name, world_name, local_x, local_y, local_z, yaw, pitch, created_by, created_at FROM island_homes WHERE island_id = ? AND name = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, name.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? java.util.Optional.of(home(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island home", exception);
        }
    }

    @Override
    public void upsertHome(UUID islandId, String name, IslandLocation location, UUID createdBy) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_homes(island_id, name, world_name, local_x, local_y, local_z, yaw, pitch, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (island_id, name) DO UPDATE SET world_name = EXCLUDED.world_name, local_x = EXCLUDED.local_x, local_y = EXCLUDED.local_y, local_z = EXCLUDED.local_z, yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch, created_by = EXCLUDED.created_by, created_at = now()")) {
            statement.setObject(1, islandId);
            statement.setString(2, name.toLowerCase());
            statement.setString(3, location.worldName());
            statement.setDouble(4, location.localX());
            statement.setDouble(5, location.localY());
            statement.setDouble(6, location.localZ());
            statement.setFloat(7, location.yaw());
            statement.setFloat(8, location.pitch());
            statement.setObject(9, createdBy);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island home", exception);
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

    @Override
    public List<UUID> publicIslandIds(int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM islands WHERE public_access = true AND deleted_at IS NULL ORDER BY random() LIMIT ?")) {
            statement.setInt(1, Math.max(0, limit));
            try (ResultSet rs = statement.executeQuery()) {
                List<UUID> result = new ArrayList<>();
                while (rs.next()) {
                    result.add((UUID) rs.getObject("id"));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read public islands", exception);
        }
    }

    private IslandInviteSnapshot lockInvite(Connection connection, UUID inviteId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, island_id, inviter_uuid, target_uuid, state, created_at, expires_at FROM island_invites WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, inviteId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? invite(rs) : null;
            }
        }
    }

    private IslandInviteSnapshot invite(ResultSet rs) throws SQLException {
        return new IslandInviteSnapshot(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("island_id"),
            (UUID) rs.getObject("inviter_uuid"),
            (UUID) rs.getObject("target_uuid"),
            rs.getString("state"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant()
        );
    }

    private IslandHomeSnapshot home(ResultSet rs) throws SQLException {
        IslandLocation location = new IslandLocation(rs.getString("world_name"), rs.getDouble("local_x"), rs.getDouble("local_y"), rs.getDouble("local_z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
        return new IslandHomeSnapshot((UUID) rs.getObject("island_id"), rs.getString("name"), location, (UUID) rs.getObject("created_by"), rs.getTimestamp("created_at").toInstant());
    }
}
