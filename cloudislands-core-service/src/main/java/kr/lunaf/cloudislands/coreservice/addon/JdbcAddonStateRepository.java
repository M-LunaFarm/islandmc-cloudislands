package kr.lunaf.cloudislands.coreservice.addon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcAddonStateRepository implements AddonStateRepository {
    private final DataSource dataSource;

    public JdbcAddonStateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<String, String> list(String addonId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT state_key, state_value FROM addon_state WHERE addon_id = ? ORDER BY state_key")) {
            statement.setString(1, AddonStateRepository.safeAddonId(addonId));
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, String> state = new HashMap<>();
                while (rs.next()) {
                    state.put(rs.getString("state_key"), rs.getString("state_value"));
                }
                return Map.copyOf(state);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read addon state", exception);
        }
    }

    @Override
    public Map<String, String> put(String addonId, String key, String value) {
        Map<String, String> values = new HashMap<>();
        values.put(key, value);
        return put(addonId, values);
    }

    @Override
    public Map<String, String> put(String addonId, Map<String, String> values) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        Map<String, String> safeValues = safeValues(values);
        if (safeValues.isEmpty()) {
            return list(safeAddonId);
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO addon_state(addon_id, state_key, state_value) VALUES (?, ?, ?) ON CONFLICT (addon_id, state_key) DO UPDATE SET state_value = EXCLUDED.state_value, updated_at = now()")) {
                ensureKeyCapacity(connection, safeAddonId, safeValues.keySet());
                for (Map.Entry<String, String> entry : safeValues.entrySet()) {
                    statement.setString(1, safeAddonId);
                    statement.setString(2, entry.getKey());
                    statement.setString(3, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return list(safeAddonId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write addon state", exception);
        }
    }

    @Override
    public Map<String, String> remove(String addonId, String key) {
        if (key == null || key.isBlank()) {
            return list(addonId);
        }
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        String safeKey = AddonStateRepository.safeKey(key);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM addon_state WHERE addon_id = ? AND state_key = ?")) {
            statement.setString(1, safeAddonId);
            statement.setString(2, safeKey);
            statement.executeUpdate();
            return list(safeAddonId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to remove addon state", exception);
        }
    }

    @Override
    public void clear(String addonId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM addon_state WHERE addon_id = ?")) {
            statement.setString(1, AddonStateRepository.safeAddonId(addonId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear addon state", exception);
        }
    }

    @Override
    public Map<String, String> listIsland(String addonId, UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT state_key, state_value FROM addon_island_state WHERE addon_id = ? AND island_id = ? ORDER BY state_key")) {
            statement.setString(1, AddonStateRepository.safeAddonId(addonId));
            statement.setObject(2, AddonStateRepository.safeIslandId(islandId));
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, String> state = new HashMap<>();
                while (rs.next()) {
                    state.put(rs.getString("state_key"), rs.getString("state_value"));
                }
                return Map.copyOf(state);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read addon island state", exception);
        }
    }

    @Override
    public Map<String, String> putIsland(String addonId, UUID islandId, String key, String value) {
        Map<String, String> values = new HashMap<>();
        values.put(key, value);
        return putIsland(addonId, islandId, values);
    }

    @Override
    public Map<String, String> putIsland(String addonId, UUID islandId, Map<String, String> values) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        UUID safeIslandId = AddonStateRepository.safeIslandId(islandId);
        Map<String, String> safeValues = safeValues(values);
        if (safeValues.isEmpty()) {
            return listIsland(safeAddonId, safeIslandId);
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO addon_island_state(addon_id, island_id, state_key, state_value) VALUES (?, ?, ?, ?) ON CONFLICT (addon_id, island_id, state_key) DO UPDATE SET state_value = EXCLUDED.state_value, updated_at = now()")) {
                ensureIslandKeyCapacity(connection, safeAddonId, safeIslandId, safeValues.keySet());
                for (Map.Entry<String, String> entry : safeValues.entrySet()) {
                    statement.setString(1, safeAddonId);
                    statement.setObject(2, safeIslandId);
                    statement.setString(3, entry.getKey());
                    statement.setString(4, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return listIsland(safeAddonId, safeIslandId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write addon island state", exception);
        }
    }

    @Override
    public Map<String, String> removeIsland(String addonId, UUID islandId, String key) {
        if (key == null || key.isBlank()) {
            return listIsland(addonId, islandId);
        }
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        UUID safeIslandId = AddonStateRepository.safeIslandId(islandId);
        String safeKey = AddonStateRepository.safeKey(key);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM addon_island_state WHERE addon_id = ? AND island_id = ? AND state_key = ?")) {
            statement.setString(1, safeAddonId);
            statement.setObject(2, safeIslandId);
            statement.setString(3, safeKey);
            statement.executeUpdate();
            return listIsland(safeAddonId, safeIslandId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to remove addon island state", exception);
        }
    }

    @Override
    public void clearIsland(String addonId, UUID islandId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM addon_island_state WHERE addon_id = ? AND island_id = ?")) {
            statement.setString(1, AddonStateRepository.safeAddonId(addonId));
            statement.setObject(2, AddonStateRepository.safeIslandId(islandId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear addon island state", exception);
        }
    }

    private Map<String, String> safeValues(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new HashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                safe.put(AddonStateRepository.safeKey(key), AddonStateRepository.safeValue(value));
            }
        });
        return Map.copyOf(safe);
    }

    private void ensureKeyCapacity(Connection connection, String addonId, Iterable<String> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS key_count, BOOL_OR(state_key = ?) AS key_exists FROM addon_state WHERE addon_id = ?")) {
            int newKeys = 0;
            int count = 0;
            for (String key : keys) {
                statement.setString(1, key);
                statement.setString(2, addonId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        continue;
                    }
                    if (!rs.getBoolean("key_exists")) {
                        newKeys++;
                    }
                    count = Math.max(count, rs.getInt("key_count"));
                }
            }
            if (count + newKeys > AddonStateRepository.MAX_KEYS_PER_ADDON) {
                throw new IllegalArgumentException("Addon state key limit reached");
            }
        }
    }

    private void ensureIslandKeyCapacity(Connection connection, String addonId, UUID islandId, Iterable<String> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS key_count, BOOL_OR(state_key = ?) AS key_exists FROM addon_island_state WHERE addon_id = ? AND island_id = ?")) {
            int newKeys = 0;
            int count = 0;
            for (String key : keys) {
                statement.setString(1, key);
                statement.setString(2, addonId);
                statement.setObject(3, islandId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        continue;
                    }
                    if (!rs.getBoolean("key_exists")) {
                        newKeys++;
                    }
                    count = Math.max(count, rs.getInt("key_count"));
                }
            }
            if (count + newKeys > AddonStateRepository.MAX_KEYS_PER_ADDON) {
                throw new IllegalArgumentException("Addon island state key limit reached");
            }
        }
    }
}
