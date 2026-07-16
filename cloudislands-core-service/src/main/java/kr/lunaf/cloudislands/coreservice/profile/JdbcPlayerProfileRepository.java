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
             PreparedStatement statement = connection.prepareStatement("SELECT uuid, last_name, primary_island_id, last_seen_at, locale, disbands_remaining, island_fly_enabled, world_border_enabled, blocks_stacker_enabled, border_color FROM player_profiles WHERE uuid = ?")) {
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
             PreparedStatement statement = connection.prepareStatement("SELECT uuid, last_name, primary_island_id, last_seen_at, locale, disbands_remaining, island_fly_enabled, world_border_enabled, blocks_stacker_enabled, border_color FROM player_profiles WHERE lower(last_name) = lower(?) ORDER BY CASE WHEN last_seen_at IS NULL THEN 1 ELSE 0 END, last_seen_at DESC LIMIT 1")) {
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
             PreparedStatement statement = connection.prepareStatement(touchSql(connection))) {
            statement.setObject(1, playerUuid);
            statement.setString(2, lastName == null ? "" : lastName);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to touch player profile", exception);
        }
    }

    @Override
    public PlayerIslandProfile touch(UUID playerUuid, String lastName, String locale) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(touchWithLocaleSql(connection))) {
            statement.setObject(1, playerUuid);
            statement.setString(2, lastName == null ? "" : lastName);
            statement.setString(3, PlayerIslandProfile.normalizeLocale(locale));
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to touch player profile locale", exception);
        }
    }

    @Override
    public PlayerIslandProfile setLocale(UUID playerUuid, String locale) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET locale = ?, updated_at = now() WHERE uuid = ?")) {
            statement.setString(1, PlayerIslandProfile.normalizeLocale(locale));
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set player profile locale", exception);
        }
    }

    @Override
    public PlayerIslandProfile setIslandFlyEnabled(UUID playerUuid, boolean enabled) {
        String preferenceKey = "island-fly";
        long revision = reservePreferenceMutation(playerUuid, preferenceKey);
        return setIslandFlyEnabledIfPreferenceCurrent(playerUuid, enabled, preferenceKey, revision)
            .orElseThrow(() -> new IllegalStateException("player island flight preference was superseded"));
    }

    @Override
    public long reservePreferenceMutation(UUID playerUuid, String preferenceKey) {
        ensure(playerUuid);
        String normalizedKey = normalizePreferenceKey(preferenceKey);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(preferenceRevisionInsertSql(connection));
                 PreparedStatement update = connection.prepareStatement("UPDATE player_preference_revisions SET revision = revision + 1, updated_at = now() WHERE player_uuid = ? AND preference_key = ?");
                 PreparedStatement read = connection.prepareStatement("SELECT revision FROM player_preference_revisions WHERE player_uuid = ? AND preference_key = ?")) {
                insert.setObject(1, playerUuid);
                insert.setString(2, normalizedKey);
                insert.executeUpdate();
                update.setObject(1, playerUuid);
                update.setString(2, normalizedKey);
                update.executeUpdate();
                read.setObject(1, playerUuid);
                read.setString(2, normalizedKey);
                long revision;
                try (ResultSet result = read.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("player preference revision disappeared while reserving mutation");
                    }
                    revision = result.getLong(1);
                }
                connection.commit();
                return revision;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to reserve player preference mutation", exception);
        }
    }

    @Override
    public Optional<PlayerIslandProfile> setIslandFlyEnabledIfPreferenceCurrent(UUID playerUuid, boolean enabled, String preferenceKey, long preferenceRevision) {
        if (preferenceRevision <= 0L) {
            return Optional.empty();
        }
        ensure(playerUuid);
        String normalizedKey = normalizePreferenceKey(preferenceKey);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement revision = connection.prepareStatement("SELECT revision FROM player_preference_revisions WHERE player_uuid = ? AND preference_key = ? FOR UPDATE");
                 PreparedStatement update = connection.prepareStatement("UPDATE player_profiles SET island_fly_enabled = ?, updated_at = now() WHERE uuid = ?")) {
                revision.setObject(1, playerUuid);
                revision.setString(2, normalizedKey);
                try (ResultSet result = revision.executeQuery()) {
                    if (!result.next() || result.getLong(1) != preferenceRevision) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                update.setBoolean(1, enabled);
                update.setObject(2, playerUuid);
                if (update.executeUpdate() != 1) {
                    throw new SQLException("player profile disappeared while applying preference mutation");
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
            return Optional.of(find(playerUuid));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to apply current player island flight preference", exception);
        }
    }

    @Override
    public PlayerIslandProfile setWorldBorderEnabled(UUID playerUuid, boolean enabled) {
        return setBooleanPreference(playerUuid, "world_border_enabled", enabled, "world border");
    }

    @Override
    public PlayerIslandProfile setBlocksStackerEnabled(UUID playerUuid, boolean enabled) {
        return setBooleanPreference(playerUuid, "blocks_stacker_enabled", enabled, "blocks stacker");
    }

    @Override
    public PlayerIslandProfile setBorderColor(UUID playerUuid, String color) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET border_color = ?, updated_at = now() WHERE uuid = ?")) {
            statement.setString(1, PlayerIslandProfile.normalizeBorderColor(color));
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set player border color preference", exception);
        }
    }

    private PlayerIslandProfile setBooleanPreference(UUID playerUuid, String column, boolean enabled, String label) {
        ensure(playerUuid);
        String sql = switch (column) {
            case "world_border_enabled" -> "UPDATE player_profiles SET world_border_enabled = ?, updated_at = now() WHERE uuid = ?";
            case "blocks_stacker_enabled" -> "UPDATE player_profiles SET blocks_stacker_enabled = ?, updated_at = now() WHERE uuid = ?";
            default -> throw new IllegalArgumentException("unsupported player preference column");
        };
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, enabled);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set player " + label + " preference", exception);
        }
    }

    @Override
    public PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = ?, primary_island_selection_revision = primary_island_selection_revision + 1, updated_at = now() WHERE uuid = ?")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set player primary island", exception);
        }
    }

    @Override
    public PlayerIslandProfile clearPrimaryIsland(UUID playerUuid) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = NULL, primary_island_selection_revision = primary_island_selection_revision + 1, updated_at = now() WHERE uuid = ?")) {
            statement.setObject(1, playerUuid);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear player primary island", exception);
        }
    }

    @Override
    public long reservePrimaryIslandSelection(UUID playerUuid) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("UPDATE player_profiles SET primary_island_selection_revision = primary_island_selection_revision + 1, updated_at = now() WHERE uuid = ?");
                 PreparedStatement read = connection.prepareStatement("SELECT primary_island_selection_revision FROM player_profiles WHERE uuid = ?")) {
                update.setObject(1, playerUuid);
                update.executeUpdate();
                read.setObject(1, playerUuid);
                long revision;
                try (ResultSet result = read.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("player profile disappeared while reserving island selection");
                    }
                    revision = result.getLong(1);
                }
                connection.commit();
                return revision;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to reserve player primary island selection", exception);
        }
    }

    @Override
    public Optional<PlayerIslandProfile> setPrimaryIslandIfSelectionCurrent(UUID playerUuid, UUID islandId, long selectionRevision) {
        if (selectionRevision <= 0L) {
            return Optional.empty();
        }
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET primary_island_id = ?, updated_at = now() WHERE uuid = ? AND primary_island_selection_revision = ?")) {
            statement.setObject(1, islandId);
            statement.setObject(2, playerUuid);
            statement.setLong(3, selectionRevision);
            return statement.executeUpdate() == 1 ? Optional.of(find(playerUuid)) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to apply current player primary island selection", exception);
        }
    }

    @Override
    public PlayerIslandProfile setDisbandsRemaining(UUID playerUuid, int value) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE player_profiles SET disbands_remaining = ?, updated_at = now() WHERE uuid = ?")) {
            statement.setInt(1, Math.max(0, value));
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to set player disbands remaining", exception);
        }
    }

    @Override
    public PlayerIslandProfile addDisbandsRemaining(UUID playerUuid, int delta) {
        ensure(playerUuid);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(addDisbandsSql(connection))) {
            statement.setInt(1, delta);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
            return find(playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to add player disbands remaining", exception);
        }
    }

    private void ensure(UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(ensureSql(connection))) {
            statement.setObject(1, playerUuid);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to ensure player profile", exception);
        }
    }

    private String touchSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO player_profiles(uuid, last_name, last_seen_at) VALUES (?, ?, now()) ON DUPLICATE KEY UPDATE last_name = VALUES(last_name), last_seen_at = now(), updated_at = now()";
        }
        return "INSERT INTO player_profiles(uuid, last_name, last_seen_at) VALUES (?, ?, now()) ON CONFLICT (uuid) DO UPDATE SET last_name = EXCLUDED.last_name, last_seen_at = now(), updated_at = now()";
    }

    private String touchWithLocaleSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT INTO player_profiles(uuid, last_name, locale, last_seen_at) VALUES (?, ?, ?, now()) ON DUPLICATE KEY UPDATE last_name = VALUES(last_name), locale = VALUES(locale), last_seen_at = now(), updated_at = now()";
        }
        return "INSERT INTO player_profiles(uuid, last_name, locale, last_seen_at) VALUES (?, ?, ?, now()) ON CONFLICT (uuid) DO UPDATE SET last_name = EXCLUDED.last_name, locale = EXCLUDED.locale, last_seen_at = now(), updated_at = now()";
    }

    private String ensureSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT IGNORE INTO player_profiles(uuid) VALUES (?)";
        }
        return "INSERT INTO player_profiles(uuid) VALUES (?) ON CONFLICT (uuid) DO NOTHING";
    }

    private String addDisbandsSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "UPDATE player_profiles SET disbands_remaining = CAST(LEAST(2147483647, GREATEST(0, CAST(disbands_remaining AS DECIMAL(11,0)) + CAST(? AS DECIMAL(11,0)))) AS SIGNED), updated_at = now() WHERE uuid = ?";
        }
        return "UPDATE player_profiles SET disbands_remaining = CAST(LEAST(2147483647, GREATEST(0, CAST(disbands_remaining AS BIGINT) + CAST(? AS BIGINT))) AS INTEGER), updated_at = now() WHERE uuid = ?";
    }

    private String preferenceRevisionInsertSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT IGNORE INTO player_preference_revisions(player_uuid, preference_key, revision) VALUES (?, ?, 0)";
        }
        return "INSERT INTO player_preference_revisions(player_uuid, preference_key, revision) VALUES (?, ?, 0) ON CONFLICT (player_uuid, preference_key) DO NOTHING";
    }

    private static String normalizePreferenceKey(String preferenceKey) {
        String normalized = preferenceKey == null ? "" : preferenceKey.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("invalid player preference key");
        }
        return normalized;
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }

    private PlayerIslandProfile profile(ResultSet rs) throws SQLException {
        UUID primaryIslandId = (UUID) rs.getObject("primary_island_id");
        return new PlayerIslandProfile(
            (UUID) rs.getObject("uuid"),
            rs.getString("last_name") == null ? "" : rs.getString("last_name"),
            Optional.ofNullable(primaryIslandId),
            rs.getTimestamp("last_seen_at") == null ? Instant.EPOCH : rs.getTimestamp("last_seen_at").toInstant(),
            rs.getString("locale"),
            rs.getInt("disbands_remaining"),
            rs.getBoolean("island_fly_enabled"),
            rs.getBoolean("world_border_enabled"),
            rs.getBoolean("blocks_stacker_enabled"),
            rs.getString("border_color")
        );
    }
}
