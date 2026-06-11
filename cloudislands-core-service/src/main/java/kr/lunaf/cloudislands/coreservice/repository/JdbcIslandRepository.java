package kr.lunaf.cloudislands.coreservice.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;

public final class JdbcIslandRepository implements IslandRepository {
    private final DataSource dataSource;

    public JdbcIslandRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<IslandSnapshot> findById(UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, owner_uuid, name, state, size, level, worth, public_access, created_at, updated_at FROM islands WHERE id = ? AND deleted_at IS NULL")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find island", exception);
        }
    }

    @Override
    public Optional<IslandSnapshot> findByOwner(UUID ownerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, owner_uuid, name, state, size, level, worth, public_access, created_at, updated_at FROM islands WHERE owner_uuid = ? AND deleted_at IS NULL")) {
            statement.setObject(1, ownerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find island by owner", exception);
        }
    }

    @Override
    public IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            lockPlayerProfile(connection, ownerUuid);
            if (findByOwnerInTransaction(connection, ownerUuid).isPresent()) {
                throw new IllegalStateException("player already owns an island");
            }
            try (PreparedStatement island = connection.prepareStatement("INSERT INTO islands(id, owner_uuid, name, state, template_id) VALUES (?, ?, ?, 'CREATING', ?)")) {
                island.setObject(1, islandId);
                island.setObject(2, ownerUuid);
                island.setString(3, name);
                island.setString(4, templateId);
                island.executeUpdate();
            }
            createOwnerMember(connection, islandId, ownerUuid);
            createRuntime(connection, islandId, "CREATING");
            connection.commit();
            return new IslandSnapshot(islandId, ownerUuid, name, IslandState.CREATING, 300, 0L, "0.00", false, Instant.now(), Instant.now());
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to create island", exception);
        }
    }

    @Override
    public boolean markDeleted(UUID islandId, UUID requesterUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE islands SET state = 'DELETED', deleted_at = now(), updated_at = now() WHERE id = ? AND owner_uuid = ? AND deleted_at IS NULL")) {
            statement.setObject(1, islandId);
            statement.setObject(2, requesterUuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark island deleted", exception);
        }
    }

    @Override
    public boolean transferOwnership(UUID islandId, UUID currentOwnerUuid, UUID newOwnerUuid) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            lockPlayerProfile(connection, currentOwnerUuid);
            lockPlayerProfile(connection, newOwnerUuid);
            if (findByOwnerInTransaction(connection, newOwnerUuid).isPresent()) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement island = connection.prepareStatement("UPDATE islands SET owner_uuid = ?, updated_at = now() WHERE id = ? AND owner_uuid = ? AND deleted_at IS NULL");
                 PreparedStatement oldOwner = connection.prepareStatement("UPDATE island_members SET role = 'CO_OWNER' WHERE island_id = ? AND player_uuid = ? AND role = 'OWNER'");
                 PreparedStatement newOwner = connection.prepareStatement("INSERT INTO island_members(island_id, player_uuid, role) VALUES (?, ?, 'OWNER') ON CONFLICT (island_id, player_uuid) DO UPDATE SET role = 'OWNER'")) {
                island.setObject(1, newOwnerUuid);
                island.setObject(2, islandId);
                island.setObject(3, currentOwnerUuid);
                if (island.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                oldOwner.setObject(1, islandId);
                oldOwner.setObject(2, currentOwnerUuid);
                oldOwner.executeUpdate();
                newOwner.setObject(1, islandId);
                newOwner.setObject(2, newOwnerUuid);
                newOwner.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to transfer island ownership", exception);
        }
    }

    @Override
    public void createOwnerMember(UUID islandId, UUID ownerUuid) {
        try (Connection connection = dataSource.getConnection()) {
            createOwnerMember(connection, islandId, ownerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to create owner member", exception);
        }
    }

    @Override
    public void createRuntime(UUID islandId, String state) {
        try (Connection connection = dataSource.getConnection()) {
            createRuntime(connection, islandId, state);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to create runtime", exception);
        }
    }

    private void lockPlayerProfile(Connection connection, UUID ownerUuid) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement("INSERT INTO player_profiles(uuid) VALUES (?) ON CONFLICT (uuid) DO NOTHING")) {
            upsert.setObject(1, ownerUuid);
            upsert.executeUpdate();
        }
        try (PreparedStatement lock = connection.prepareStatement("SELECT uuid FROM player_profiles WHERE uuid = ? FOR UPDATE")) {
            lock.setObject(1, ownerUuid);
            lock.executeQuery().close();
        }
    }

    private Optional<IslandSnapshot> findByOwnerInTransaction(Connection connection, UUID ownerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, owner_uuid, name, state, size, level, worth, public_access, created_at, updated_at FROM islands WHERE owner_uuid = ? AND deleted_at IS NULL FOR UPDATE")) {
            statement.setObject(1, ownerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private void createOwnerMember(Connection connection, UUID islandId, UUID ownerUuid) throws SQLException {
        try (PreparedStatement member = connection.prepareStatement("INSERT INTO island_members(island_id, player_uuid, role) VALUES (?, ?, 'OWNER')")) {
            member.setObject(1, islandId);
            member.setObject(2, ownerUuid);
            member.executeUpdate();
        }
    }

    private void createRuntime(Connection connection, UUID islandId, String state) throws SQLException {
        try (PreparedStatement runtime = connection.prepareStatement("INSERT INTO island_runtime(island_id, state) VALUES (?, ?)")) {
            runtime.setObject(1, islandId);
            runtime.setString(2, state);
            runtime.executeUpdate();
        }
    }

    private IslandSnapshot map(ResultSet rs) throws SQLException {
        return new IslandSnapshot(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("owner_uuid"),
            rs.getString("name"),
            IslandState.valueOf(rs.getString("state")),
            rs.getInt("size"),
            rs.getLong("level"),
            rs.getBigDecimal("worth").toPlainString(),
            rs.getBoolean("public_access"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
