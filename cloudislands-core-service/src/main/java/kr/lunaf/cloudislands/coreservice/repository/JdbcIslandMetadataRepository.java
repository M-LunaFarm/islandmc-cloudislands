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
import java.util.Optional;
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
import kr.lunaf.cloudislands.coreservice.IslandPlacement;

public final class JdbcIslandMetadataRepository implements IslandMetadataRepository {
    private final DataSource dataSource;

    public JdbcIslandMetadataRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<IslandMemberSnapshot> members(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, player_uuid, role, joined_at, trusted_expires_at FROM island_members WHERE island_id = ? AND (trusted_expires_at IS NULL OR trusted_expires_at > CURRENT_TIMESTAMP) ORDER BY joined_at")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandMemberSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(member(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island members", exception);
        }
    }

    @Override
    public List<IslandMemberSnapshot> islandsForMember(UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, player_uuid, role, joined_at, trusted_expires_at FROM island_members WHERE player_uuid = ? AND (trusted_expires_at IS NULL OR trusted_expires_at > CURRENT_TIMESTAMP) ORDER BY joined_at")) {
            statement.setObject(1, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandMemberSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(member(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read player island memberships", exception);
        }
    }

    @Override
    public boolean isMember(UUID islandId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM island_members WHERE island_id = ? AND player_uuid = ? AND (trusted_expires_at IS NULL OR trusted_expires_at > CURRENT_TIMESTAMP)")) {
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
    @Deprecated(forRemoval = false)
    @SuppressWarnings("deprecation")
    public void upsertMember(UUID islandId, UUID playerUuid, IslandRole role) {
        upsertMember(islandId, playerUuid, role, null);
    }

    @Override
    public void upsertMemberKey(UUID islandId, UUID playerUuid, String roleKey) {
        upsertMemberKey(islandId, playerUuid, roleKey, null);
    }

    @Override
    public void upsertMemberKeyAndInitializePrimary(UUID islandId, UUID playerUuid, String roleKey) {
        String result = upsertMemberKeyAndInitializePrimary(islandId, playerUuid, roleKey, Long.MAX_VALUE, Long.MAX_VALUE);
        if (!"APPLIED".equals(result) && !"UNCHANGED".equals(result)) {
            throw new IllegalStateException("unexpected member limit result: " + result);
        }
    }

    @Override
    public String upsertMemberKeyAndInitializePrimary(UUID islandId, UUID playerUuid, String roleKey, long maxMembers, long maxRoleMembers) {
        String normalizedRoleKey = kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository.normalizeRoleKey(roleKey);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                lock.setObject(1, islandId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                }
            }
            CurrentMemberState current = currentMemberState(connection, islandId, playerUuid);
            String currentRole = current.roleKey();
            boolean unchanged = current.present() && normalizedRoleKey.equals(currentRole) && current.expiresAt() == null;
            boolean addingTeamMember = !kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.teamMemberRole(currentRole)
                && kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.teamMemberRole(normalizedRoleKey);
            if (addingTeamMember && teamMemberCount(connection, islandId) >= Math.max(0L, maxMembers)) {
                connection.rollback();
                return "MEMBER_LIMIT";
            }
            if (!normalizedRoleKey.equals(currentRole) && roleMemberCount(connection, islandId, normalizedRoleKey) >= Math.max(0L, maxRoleMembers)) {
                connection.rollback();
                return "ROLE_LIMIT";
            }
            try (PreparedStatement member = connection.prepareStatement(upsertMemberSql(connection));
                 PreparedStatement ensureProfile = connection.prepareStatement(ensurePlayerProfileSql(connection));
                 PreparedStatement primary = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = ?, updated_at = now() WHERE uuid = ? AND primary_island_id IS NULL")) {
                if (!unchanged) {
                    member.setObject(1, islandId);
                    member.setObject(2, playerUuid);
                    member.setString(3, normalizedRoleKey);
                    member.setObject(4, null);
                    member.executeUpdate();
                }
                ensureProfile.setObject(1, playerUuid);
                ensureProfile.executeUpdate();
                primary.setObject(1, islandId);
                primary.setObject(2, playerUuid);
                primary.executeUpdate();
            }
            connection.commit();
            return unchanged ? "UNCHANGED" : "APPLIED";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to add island member and initialize primary island", exception);
        }
    }

    private static String currentMemberRole(Connection connection, UUID islandId, UUID playerUuid) throws SQLException {
        return currentMemberState(connection, islandId, playerUuid).roleKey();
    }

    private static CurrentMemberState currentMemberState(Connection connection, UUID islandId, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT role, trusted_expires_at FROM island_members WHERE island_id = ? AND player_uuid = ? FOR UPDATE")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new CurrentMemberState(false, "", null);
                }
                java.sql.Timestamp expiresAt = result.getTimestamp("trusted_expires_at");
                return new CurrentMemberState(true, result.getString("role"), expiresAt == null ? null : expiresAt.toInstant());
            }
        }
    }

    private record CurrentMemberState(boolean present, String roleKey, Instant expiresAt) {
    }

    private static long roleMemberCount(Connection connection, UUID islandId, String roleKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM island_members WHERE island_id = ? AND role = ? AND (trusted_expires_at IS NULL OR trusted_expires_at > CURRENT_TIMESTAMP)")) {
            statement.setObject(1, islandId);
            statement.setString(2, roleKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    @Override
    @Deprecated(forRemoval = false)
    @SuppressWarnings("deprecation")
    public void upsertMember(UUID islandId, UUID playerUuid, IslandRole role, Instant expiresAt) {
        upsertMemberKey(islandId, playerUuid, role.name(), expiresAt);
    }

    @Override
    public void upsertMemberKey(UUID islandId, UUID playerUuid, String roleKey, Instant expiresAt) {
        String normalizedRoleKey = kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository.normalizeRoleKey(roleKey);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertMemberSql(connection))) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.setString(3, normalizedRoleKey);
            statement.setObject(4, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island member", exception);
        }
    }

    @Override
    public String upsertMemberKeyWithRoleLimit(UUID islandId, UUID playerUuid, String roleKey, Instant expiresAt, long maxRoleMembers) {
        String normalizedRoleKey = kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository.normalizeRoleKey(roleKey);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                lock.setObject(1, islandId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                }
            }
            CurrentMemberState current = currentMemberState(connection, islandId, playerUuid);
            String currentRole = current.roleKey();
            boolean unchanged = current.present() && normalizedRoleKey.equals(currentRole) && expiresAt == null && current.expiresAt() == null;
            if (!normalizedRoleKey.equals(currentRole) && roleMemberCount(connection, islandId, normalizedRoleKey) >= Math.max(0L, maxRoleMembers)) {
                connection.rollback();
                return "ROLE_LIMIT";
            }
            if (!unchanged) {
                try (PreparedStatement member = connection.prepareStatement(upsertMemberSql(connection))) {
                    member.setObject(1, islandId);
                    member.setObject(2, playerUuid);
                    member.setString(3, normalizedRoleKey);
                    member.setObject(4, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
                    member.executeUpdate();
                }
            }
            connection.commit();
            return unchanged ? "UNCHANGED" : "APPLIED";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island member within role limit", exception);
        }
    }

    @Override
    public void removeMember(UUID islandId, UUID playerUuid) {
        removeMemberAndClearPrimaryResult(islandId, playerUuid);
    }

    @Override
    public void removeMemberAndClearPrimary(UUID islandId, UUID playerUuid) {
        removeMemberAndClearPrimaryResult(islandId, playerUuid);
    }

    @Override
    public boolean removeMemberAndClearPrimaryResult(UUID islandId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement member = connection.prepareStatement("DELETE FROM island_members WHERE island_id = ? AND player_uuid = ? AND role <> 'OWNER' AND (trusted_expires_at IS NULL OR trusted_expires_at > CURRENT_TIMESTAMP)");
             PreparedStatement profile = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = NULL, updated_at = now() WHERE uuid = ? AND primary_island_id = ? AND NOT EXISTS (SELECT 1 FROM islands WHERE id = ? AND owner_uuid = ? AND deleted_at IS NULL)")) {
            connection.setAutoCommit(false);
            member.setObject(1, islandId);
            member.setObject(2, playerUuid);
            boolean removed = member.executeUpdate() > 0;
            profile.setObject(1, playerUuid);
            profile.setObject(2, islandId);
            profile.setObject(3, islandId);
            profile.setObject(4, playerUuid);
            profile.executeUpdate();
            connection.commit();
            return removed;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to remove island member and clear primary island", exception);
        }
    }

    @Override
    public IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid) {
        UUID inviteId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(86400);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lockIsland = connection.prepareStatement("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                lockIsland.setObject(1, islandId);
                try (ResultSet result = lockIsland.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        throw new IllegalArgumentException("island was not found");
                    }
                }
            }
            try (PreparedStatement expire = connection.prepareStatement("UPDATE island_invites SET state = 'EXPIRED' WHERE island_id = ? AND target_uuid = ? AND state = 'PENDING'")) {
                expire.setObject(1, islandId);
                expire.setObject(2, targetUuid);
                expire.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO island_invites(id, island_id, inviter_uuid, target_uuid, state, expires_at) VALUES (?, ?, ?, ?, 'PENDING', ?)")) {
                statement.setObject(1, inviteId);
                statement.setObject(2, islandId);
                statement.setObject(3, inviterUuid);
                statement.setObject(4, targetUuid);
                statement.setObject(5, java.sql.Timestamp.from(expiresAt));
                statement.executeUpdate();
                connection.commit();
                return new IslandInviteSnapshot(inviteId, islandId, inviterUuid, targetUuid, "PENDING", now, expiresAt);
            }
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
        return "APPLIED".equals(acceptInviteResult(inviteId, playerUuid, Long.MAX_VALUE));
    }

    @Override
    public boolean acceptInvite(UUID inviteId, UUID playerUuid, long maxMembers) {
        return "APPLIED".equals(acceptInviteResult(inviteId, playerUuid, maxMembers));
    }

    @Override
    public String acceptInviteResult(UUID inviteId, UUID playerUuid, long maxMembers) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            IslandInviteSnapshot invite = lockInvite(connection, inviteId);
            if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING") || !invite.expiresAt().isAfter(Instant.now())) {
                connection.rollback();
                return "INVITE_UNAVAILABLE";
            }
            try (PreparedStatement lockIsland = connection.prepareStatement("SELECT owner_uuid FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                lockIsland.setObject(1, invite.islandId());
                try (ResultSet result = lockIsland.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                    if (playerUuid.equals((UUID) result.getObject("owner_uuid"))) {
                        expireInvite(connection, inviteId);
                        connection.commit();
                        return "ALREADY_MEMBER";
                    }
                }
            }
            if (teamMemberExists(connection, invite.islandId(), playerUuid)) {
                expireInvite(connection, inviteId);
                connection.commit();
                return "ALREADY_MEMBER";
            }
            if (teamMemberCount(connection, invite.islandId()) >= Math.max(0L, maxMembers)) {
                connection.rollback();
                return "MEMBER_LIMIT";
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE island_invites SET state = 'ACCEPTED' WHERE id = ?");
                 PreparedStatement member = connection.prepareStatement(acceptInviteMemberSql(connection));
                 PreparedStatement ensureProfile = connection.prepareStatement(ensurePlayerProfileSql(connection));
                 PreparedStatement primary = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = ?, updated_at = now() WHERE uuid = ? AND primary_island_id IS NULL")) {
                update.setObject(1, inviteId);
                update.executeUpdate();
                member.setObject(1, invite.islandId());
                member.setObject(2, playerUuid);
                member.executeUpdate();
                ensureProfile.setObject(1, playerUuid);
                ensureProfile.executeUpdate();
                primary.setObject(1, invite.islandId());
                primary.setObject(2, playerUuid);
                primary.executeUpdate();
            }
            connection.commit();
            return "APPLIED";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to accept island invite", exception);
        }
    }

    private static void expireInvite(Connection connection, UUID inviteId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE island_invites SET state = 'EXPIRED' WHERE id = ?")) {
            statement.setObject(1, inviteId);
            statement.executeUpdate();
        }
    }

    private static boolean teamMemberExists(Connection connection, UUID islandId, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT role FROM island_members WHERE island_id = ? AND player_uuid = ? AND (trusted_expires_at IS NULL OR trusted_expires_at > CURRENT_TIMESTAMP)")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.teamMemberRole(result.getString("role"));
            }
        }
    }

    private static long teamMemberCount(Connection connection, UUID islandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT role FROM island_members WHERE island_id = ? AND (trusted_expires_at IS NULL OR trusted_expires_at > CURRENT_TIMESTAMP)")) {
            statement.setObject(1, islandId);
            try (ResultSet result = statement.executeQuery()) {
                long count = 0L;
                while (result.next()) {
                    if (kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.teamMemberRole(result.getString("role"))) {
                        count++;
                    }
                }
                return count;
            }
        }
    }

    @Override
    public boolean declineInvite(UUID inviteId, UUID playerUuid) {
        return "APPLIED".equals(declineInviteResult(inviteId, playerUuid));
    }

    @Override
    public String declineInviteResult(UUID inviteId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            IslandInviteSnapshot invite = lockInvite(connection, inviteId);
            if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING")) {
                connection.rollback();
                return "INVITE_UNAVAILABLE";
            }
            if (!invite.expiresAt().isAfter(Instant.now())) {
                try (PreparedStatement expired = connection.prepareStatement("UPDATE island_invites SET state = 'EXPIRED' WHERE id = ?")) {
                    expired.setObject(1, inviteId);
                    expired.executeUpdate();
                }
                connection.commit();
                return "EXPIRED";
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE island_invites SET state = 'DECLINED' WHERE id = ?")) {
                statement.setObject(1, inviteId);
                statement.executeUpdate();
            }
            connection.commit();
            return "APPLIED";
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
        String result = banVisitorResult(islandId, actorUuid, playerUuid, reason);
        if (!"APPLIED".equals(result)) {
            throw new IllegalStateException("visitor ban rejected: " + result);
        }
    }

    @Override
    public String banVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement island = connection.prepareStatement("SELECT owner_uuid FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                island.setObject(1, islandId);
                try (ResultSet result = island.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                    if (playerUuid.equals((UUID) result.getObject("owner_uuid"))) {
                        connection.rollback();
                        return "VISITOR_BAN_DENIED";
                    }
                }
            }
            String currentRole = currentMemberRole(connection, islandId, playerUuid);
            if (kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.memberRole(currentRole)) {
                connection.rollback();
                return "VISITOR_BAN_DENIED";
            }
            try (PreparedStatement statement = connection.prepareStatement(banVisitorSql(connection))) {
                statement.setObject(1, islandId);
                statement.setObject(2, playerUuid);
                statement.setObject(3, actorUuid);
                statement.setString(4, reason);
                statement.executeUpdate();
            }
            connection.commit();
            return "APPLIED";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to ban island visitor", exception);
        }
    }

    @Override
    public void pardonVisitor(UUID islandId, UUID playerUuid) {
        pardonVisitorResult(islandId, playerUuid);
    }

    @Override
    public String pardonVisitorResult(UUID islandId, UUID playerUuid) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement island = connection.prepareStatement("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                island.setObject(1, islandId);
                try (ResultSet result = island.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                }
            }
            boolean removed;
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM island_bans WHERE island_id = ? AND banned_uuid = ? AND (expires_at IS NULL OR expires_at > now())")) {
                statement.setObject(1, islandId);
                statement.setObject(2, playerUuid);
                removed = statement.executeUpdate() > 0;
            }
            connection.commit();
            return removed ? "APPLIED" : "BAN_NOT_FOUND";
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
        setLockedResult(islandId, locked);
    }

    @Override
    public boolean setLockedResult(UUID islandId, boolean locked) {
        return !"ISLAND_NOT_FOUND".equals(setLockedMutationResult(islandId, locked));
    }

    @Override
    public String setLockedMutationResult(UUID islandId, boolean locked) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            boolean current;
            try (PreparedStatement lock = connection.prepareStatement("SELECT locked FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                lock.setObject(1, islandId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                    current = result.getBoolean("locked");
                }
            }
            if (current == locked) {
                connection.commit();
                return "UNCHANGED";
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE islands SET locked = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                statement.setBoolean(1, locked);
                statement.setObject(2, islandId);
                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    return "ISLAND_NOT_FOUND";
                }
            }
            connection.commit();
            return "APPLIED";
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
        setFlagResult(islandId, flag, value);
    }

    @Override
    public String setFlagResult(UUID islandId, IslandFlag flag, String value) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement island = connection.prepareStatement("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                island.setObject(1, islandId);
                try (ResultSet result = island.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                }
            }
            try (PreparedStatement current = connection.prepareStatement("SELECT flag_value FROM island_flags WHERE island_id = ? AND flag_key = ? FOR UPDATE")) {
                current.setObject(1, islandId);
                current.setString(2, flag.name());
                try (ResultSet result = current.executeQuery()) {
                    if (result.next() && java.util.Objects.equals(result.getString("flag_value"), value)) {
                        connection.commit();
                        return "UNCHANGED";
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(setFlagSql(connection))) {
                statement.setObject(1, islandId);
                statement.setString(2, flag.name());
                statement.setString(3, value);
                statement.executeUpdate();
            }
            connection.commit();
            return "APPLIED";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set island flag", exception);
        }
    }

    @Override
    public boolean resetFlags(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_flags WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to reset island flags", exception);
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
        setBiomeResult(islandId, biomeKey, updatedBy);
    }

    @Override
    public String setBiomeResult(UUID islandId, String biomeKey, UUID updatedBy) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement island = connection.prepareStatement("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
                island.setObject(1, islandId);
                try (ResultSet result = island.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return "ISLAND_NOT_FOUND";
                    }
                }
            }
            try (PreparedStatement current = connection.prepareStatement("SELECT biome_key FROM island_biomes WHERE island_id = ? FOR UPDATE")) {
                current.setObject(1, islandId);
                try (ResultSet result = current.executeQuery()) {
                    String currentBiome = result.next() ? result.getString("biome_key") : "minecraft:plains";
                    if (java.util.Objects.equals(currentBiome, biomeKey)) {
                        connection.commit();
                        return "UNCHANGED";
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(setBiomeSql(connection))) {
                statement.setObject(1, islandId);
                statement.setString(2, biomeKey);
                statement.setObject(3, updatedBy);
                statement.executeUpdate();
            }
            connection.commit();
            return "APPLIED";
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
            statement.setString(2, normalizeResourceName(name));
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
             PreparedStatement statement = connection.prepareStatement(upsertHomeSql(connection))) {
            statement.setObject(1, islandId);
            statement.setString(2, normalizeResourceName(name));
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
    public String upsertHomeWithLimit(UUID islandId, String name, IslandLocation location, UUID createdBy, long maxHomes) {
        String normalizedName = normalizeResourceName(name);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            if (!lockIslandForLimitedResource(connection, islandId)) {
                connection.rollback();
                return "ISLAND_NOT_FOUND";
            }
            boolean existingHome = namedResourceExists(connection, "island_homes", islandId, normalizedName);
            if (!existingHome && namedResourceCount(connection, "island_homes", islandId) >= Math.max(0L, maxHomes)) {
                connection.rollback();
                return "HOME_LIMIT";
            }
            try (PreparedStatement statement = connection.prepareStatement(upsertHomeSql(connection))) {
                statement.setObject(1, islandId);
                statement.setString(2, normalizedName);
                statement.setString(3, location.worldName());
                statement.setDouble(4, location.localX());
                statement.setDouble(5, location.localY());
                statement.setDouble(6, location.localZ());
                statement.setFloat(7, location.yaw());
                statement.setFloat(8, location.pitch());
                statement.setObject(9, createdBy);
                statement.executeUpdate();
            }
            connection.commit();
            return existingHome ? "UPDATED" : "CREATED";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island home within limit", exception);
        }
    }

    @Override
    public List<IslandWarpSnapshot> warps(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, name, category, world_name, local_x, local_y, local_z, yaw, pitch, public_access, created_by, created_at FROM island_warps WHERE island_id = ? ORDER BY name")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandWarpSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(warpSnapshot(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island warps", exception);
        }
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit) {
        return publicWarps(limit, "", "");
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit, String category, String query) {
        String normalizedCategory = IslandWarpSnapshot.normalizeCategory(category);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(publicWarpsSql(connection, category, query))) {
            int index = 1;
            if (category != null && !category.isBlank()) {
                statement.setString(index++, normalizedCategory);
            }
            if (!normalizedQuery.isBlank()) {
                statement.setString(index++, "%" + normalizedQuery + "%");
                statement.setString(index++, "%" + normalizedQuery + "%");
            }
            statement.setInt(index, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandWarpSnapshot> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(warpSnapshot(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read public island warps", exception);
        }
    }

    @Override
    public Optional<IslandWarpSnapshot> warp(UUID islandId, String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT island_id, name, category, world_name, local_x, local_y, local_z, yaw, pitch, public_access, created_by, created_at FROM island_warps WHERE island_id = ? AND name = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, normalizeResourceName(name));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(warpSnapshot(rs));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island warp", exception);
        }
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy) {
        upsertWarp(islandId, name, location, publicAccess, createdBy, "default");
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, String category) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertWarpSql(connection))) {
            statement.setObject(1, islandId);
            statement.setString(2, normalizeResourceName(name));
            statement.setString(3, IslandWarpSnapshot.normalizeCategory(category));
            statement.setString(4, normalizeWorldName(islandId, location.worldName()));
            statement.setDouble(5, location.localX());
            statement.setDouble(6, location.localY());
            statement.setDouble(7, location.localZ());
            statement.setFloat(8, location.yaw());
            statement.setFloat(9, location.pitch());
            statement.setBoolean(10, publicAccess);
            statement.setObject(11, createdBy);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island warp", exception);
        }
    }

    @Override
    public String upsertWarpWithLimit(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, String category, long maxWarps) {
        String normalizedName = normalizeResourceName(name);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            if (!lockIslandForLimitedResource(connection, islandId)) {
                connection.rollback();
                return "ISLAND_NOT_FOUND";
            }
            boolean existingWarp = namedResourceExists(connection, "island_warps", islandId, normalizedName);
            if (!existingWarp && namedResourceCount(connection, "island_warps", islandId) >= Math.max(0L, maxWarps)) {
                connection.rollback();
                return "WARP_LIMIT";
            }
            try (PreparedStatement statement = connection.prepareStatement(upsertWarpSql(connection))) {
                statement.setObject(1, islandId);
                statement.setString(2, normalizedName);
                statement.setString(3, IslandWarpSnapshot.normalizeCategory(category));
                statement.setString(4, normalizeWorldName(islandId, location.worldName()));
                statement.setDouble(5, location.localX());
                statement.setDouble(6, location.localY());
                statement.setDouble(7, location.localZ());
                statement.setFloat(8, location.yaw());
                statement.setFloat(9, location.pitch());
                statement.setBoolean(10, publicAccess);
                statement.setObject(11, createdBy);
                statement.executeUpdate();
            }
            connection.commit();
            return existingWarp ? "UPDATED" : "CREATED";
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island warp within limit", exception);
        }
    }

    private static boolean lockIslandForLimitedResource(Connection connection, UUID islandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE")) {
            statement.setObject(1, islandId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean namedResourceExists(Connection connection, String table, UUID islandId, String name) throws SQLException {
        String sql = switch (table) {
            case "island_homes" -> "SELECT 1 FROM island_homes WHERE island_id = ? AND name = ?";
            case "island_warps" -> "SELECT 1 FROM island_warps WHERE island_id = ? AND name = ?";
            default -> throw new IllegalArgumentException("unsupported limited resource table");
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, islandId);
            statement.setString(2, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static long namedResourceCount(Connection connection, String table, UUID islandId) throws SQLException {
        String sql = switch (table) {
            case "island_homes" -> "SELECT count(*) FROM island_homes WHERE island_id = ?";
            case "island_warps" -> "SELECT count(*) FROM island_warps WHERE island_id = ?";
            default -> throw new IllegalArgumentException("unsupported limited resource table");
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, islandId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    @Override
    public void setWarpPublicAccess(UUID islandId, String name, boolean publicAccess) {
        setWarpPublicAccessResult(islandId, name, publicAccess);
    }

    @Override
    public boolean setWarpPublicAccessResult(UUID islandId, String name, boolean publicAccess) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_warps SET public_access = ? WHERE island_id = ? AND name = ?")) {
            statement.setBoolean(1, publicAccess);
            statement.setObject(2, islandId);
            statement.setString(3, normalizeResourceName(name));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update island warp access", exception);
        }
    }

    @Override
    public void deleteWarp(UUID islandId, String name) {
        deleteWarpResult(islandId, name);
    }

    @Override
    public boolean deleteWarpResult(UUID islandId, String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_warps WHERE island_id = ? AND name = ?")) {
            statement.setObject(1, islandId);
            statement.setString(2, normalizeResourceName(name));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to delete island warp", exception);
        }
    }

    @Override
    public boolean isPublicAccess(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT public_access FROM islands WHERE id = ? AND deleted_at IS NULL")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean("public_access");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island access", exception);
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
             PreparedStatement statement = connection.prepareStatement(publicIslandIdsSql(connection))) {
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

    @Override
    public List<UUID> publicIslandIdsPage(int offset, int limit) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id FROM islands WHERE public_access = true AND locked = false AND deleted_at IS NULL ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?")) {
            statement.setInt(1, Math.max(0, limit));
            statement.setInt(2, Math.max(0, offset));
            try (ResultSet rs = statement.executeQuery()) {
                List<UUID> result = new ArrayList<>();
                while (rs.next()) {
                    result.add((UUID) rs.getObject("id"));
                }
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read public island page", exception);
        }
    }

    private String upsertMemberSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_members(island_id, player_uuid, role, trusted_expires_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE role = VALUES(role), trusted_expires_at = VALUES(trusted_expires_at)";
        }
        return "INSERT INTO island_members(island_id, player_uuid, role, trusted_expires_at) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, player_uuid) DO UPDATE SET role = EXCLUDED.role, trusted_expires_at = EXCLUDED.trusted_expires_at";
    }

    private IslandMemberSnapshot member(ResultSet rs) throws SQLException {
        java.sql.Timestamp expiresAt = rs.getTimestamp("trusted_expires_at");
        return new IslandMemberSnapshot(
            (UUID) rs.getObject("island_id"),
            (UUID) rs.getObject("player_uuid"),
            rs.getString("role"),
            rs.getTimestamp("joined_at").toInstant(),
            expiresAt == null ? null : expiresAt.toInstant()
        );
    }

    private String acceptInviteMemberSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_members(island_id, player_uuid, role, trusted_expires_at) VALUES (?, ?, 'MEMBER', NULL) ON DUPLICATE KEY UPDATE role = VALUES(role), trusted_expires_at = NULL";
        }
        return "INSERT INTO island_members(island_id, player_uuid, role, trusted_expires_at) VALUES (?, ?, 'MEMBER', NULL) ON CONFLICT (island_id, player_uuid) DO UPDATE SET role = EXCLUDED.role, trusted_expires_at = NULL";
    }

    private String ensurePlayerProfileSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT IGNORE INTO player_profiles(uuid) VALUES (?)";
        }
        return "INSERT INTO player_profiles(uuid) VALUES (?) ON CONFLICT (uuid) DO NOTHING";
    }

    private String banVisitorSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_bans(island_id, banned_uuid, actor_uuid, reason) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE actor_uuid = VALUES(actor_uuid), reason = VALUES(reason), created_at = now(), expires_at = NULL";
        }
        return "INSERT INTO island_bans(island_id, banned_uuid, actor_uuid, reason) VALUES (?, ?, ?, ?) ON CONFLICT (island_id, banned_uuid) DO UPDATE SET actor_uuid = EXCLUDED.actor_uuid, reason = EXCLUDED.reason, created_at = now(), expires_at = NULL";
    }

    private String setFlagSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_flags(island_id, flag_key, flag_value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value), updated_at = now()";
        }
        return "INSERT INTO island_flags(island_id, flag_key, flag_value) VALUES (?, ?, ?) ON CONFLICT (island_id, flag_key) DO UPDATE SET flag_value = EXCLUDED.flag_value, updated_at = now()";
    }

    private String setBiomeSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_biomes(island_id, biome_key, updated_by) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE biome_key = VALUES(biome_key), updated_by = VALUES(updated_by), updated_at = now()";
        }
        return "INSERT INTO island_biomes(island_id, biome_key, updated_by) VALUES (?, ?, ?) ON CONFLICT (island_id) DO UPDATE SET biome_key = EXCLUDED.biome_key, updated_by = EXCLUDED.updated_by, updated_at = now()";
    }

    private String upsertHomeSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_homes(island_id, name, world_name, local_x, local_y, local_z, yaw, pitch, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world_name = VALUES(world_name), local_x = VALUES(local_x), local_y = VALUES(local_y), local_z = VALUES(local_z), yaw = VALUES(yaw), pitch = VALUES(pitch), created_by = VALUES(created_by), created_at = now()";
        }
        return "INSERT INTO island_homes(island_id, name, world_name, local_x, local_y, local_z, yaw, pitch, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (island_id, name) DO UPDATE SET world_name = EXCLUDED.world_name, local_x = EXCLUDED.local_x, local_y = EXCLUDED.local_y, local_z = EXCLUDED.local_z, yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch, created_by = EXCLUDED.created_by, created_at = now()";
    }

    private String upsertWarpSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO island_warps(island_id, name, category, world_name, local_x, local_y, local_z, yaw, pitch, public_access, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE category = VALUES(category), world_name = VALUES(world_name), local_x = VALUES(local_x), local_y = VALUES(local_y), local_z = VALUES(local_z), yaw = VALUES(yaw), pitch = VALUES(pitch), public_access = VALUES(public_access)";
        }
        return "INSERT INTO island_warps(island_id, name, category, world_name, local_x, local_y, local_z, yaw, pitch, public_access, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (island_id, name) DO UPDATE SET category = EXCLUDED.category, world_name = EXCLUDED.world_name, local_x = EXCLUDED.local_x, local_y = EXCLUDED.local_y, local_z = EXCLUDED.local_z, yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch, public_access = EXCLUDED.public_access";
    }

    private String publicWarpsSql(Connection connection, String category, String query) {
        StringBuilder sql = new StringBuilder("SELECT island_id, name, category, world_name, local_x, local_y, local_z, yaw, pitch, public_access, created_by, created_at FROM island_warps WHERE public_access = true");
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (lower(name) LIKE ? OR lower(category) LIKE ?)");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        return sql.toString();
    }

    private IslandWarpSnapshot warpSnapshot(ResultSet rs) throws SQLException {
        UUID islandId = (UUID) rs.getObject("island_id");
        IslandLocation location = new IslandLocation(normalizeWorldName(islandId, rs.getString("world_name")), rs.getDouble("local_x"), rs.getDouble("local_y"), rs.getDouble("local_z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
        return new IslandWarpSnapshot(islandId, rs.getString("name"), location, rs.getBoolean("public_access"), (UUID) rs.getObject("created_by"), rs.getTimestamp("created_at").toInstant(), rs.getString("category"));
    }

    private static String normalizeWorldName(UUID islandId, String worldName) {
        return worldName == null || worldName.isBlank() ? IslandPlacement.worldName(islandId) : worldName.trim();
    }

    private static String normalizeResourceName(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String publicIslandIdsSql(Connection connection) throws SQLException {
        String randomFunction = mysqlLike(connection) ? "RAND()" : "random()";
        return "SELECT id FROM islands WHERE public_access = true AND locked = false AND deleted_at IS NULL ORDER BY " + randomFunction + " LIMIT ?";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
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
