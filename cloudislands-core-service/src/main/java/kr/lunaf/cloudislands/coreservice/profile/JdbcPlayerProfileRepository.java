package kr.lunaf.cloudislands.coreservice.profile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

public final class JdbcPlayerProfileRepository implements PlayerProfileRepository {
    private final DataSource dataSource;

    public JdbcPlayerProfileRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlayerIslandProfile find(UUID playerUuid) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid, last_name, primary_island_id, last_seen_at FROM player_profiles WHERE uuid = ?")) {
            statement.setObject(1, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? profile(rs) : new PlayerIslandProfile(playerUuid, "", Optional.empty(), Instant.EPOCH);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read player profile", exception);
        }
    }

    @Override
    public Optional<PlayerIslandProfile> findByLastName(String lastName) {
        if (lastName == null || lastName.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid, last_name, primary_island_id, last_seen_at FROM player_profiles WHERE lower(last_name) = lower(?) ORDER BY last_seen_at DESC NULLS LAST LIMIT 1")) {
            statement.setString(1, lastName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(profile(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read player profile by name", exception);
        }
    }

    @Override
    public PlayerIslandProfile touch(UUID playerUuid, String lastName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO player_profiles(uuid, last_name, last_seen_at) VALUES (?, ?, now()) ON CONFLICT (uuid) DO UPDATE SET last_name = EXCLUDED.last_name, last_seen_at = now(), updated_at = now() RETURNING uuid, last_name, primary_island_id, last_seen_at")) {
            statement.setObject(1, playerUuid);
            statement.setString(2, lastName == null ? "" : lastName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? profile(rs) : find(playerUuid);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to touch player profile", exception);
        }
    }

    @Override
    public PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = ?, updated_at = now() WHERE uuid = ? RETURNING uuid, last_name, primary_island_id, last_seen_at")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? profile(rs) : find(playerUuid);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set player primary island", exception);
        }
    }

    @Override
    public PlayerIslandProfile clearPrimaryIsland(UUID playerUuid) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = NULL, updated_at = now() WHERE uuid = ? RETURNING uuid, last_name, primary_island_id, last_seen_at")) {
            statement.setObject(1, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? profile(rs) : find(playerUuid);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear player primary island", exception);
        }
    }

    private void ensure(UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO player_profiles(uuid) VALUES (?) ON CONFLICT (uuid) DO NOTHING")) {
            statement.setObject(1, playerUuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to ensure player profile", exception);
        }
    }

    private PlayerIslandProfile profile(ResultSet rs) throws SQLException {
        UUID primaryIslandId = (UUID) rs.getObject("primary_island_id");
        return new PlayerIslandProfile(
            (UUID) rs.getObject("uuid"),
            rs.getString("last_name") == null ? "" : rs.getString("last_name"),
            Optional.ofNullable(primaryIslandId),
            rs.getTimestamp("last_seen_at") == null ? Instant.EPOCH : rs.getTimestamp("last_seen_at").toInstant()
        );
    }
}
