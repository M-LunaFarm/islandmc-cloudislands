package kr.lunaf.cloudislands.coreservice.addon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
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
            statement.setString(1, safeAddonId(addonId));
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
        if (key == null || key.isBlank() || value == null) {
            return list(addonId);
        }
        String safeAddonId = safeAddonId(addonId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO addon_state(addon_id, state_key, state_value) VALUES (?, ?, ?) ON CONFLICT (addon_id, state_key) DO UPDATE SET state_value = EXCLUDED.state_value, updated_at = now()")) {
            statement.setString(1, safeAddonId);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.executeUpdate();
            return list(safeAddonId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write addon state", exception);
        }
    }

    @Override
    public Map<String, String> remove(String addonId, String key) {
        if (key == null) {
            return list(addonId);
        }
        String safeAddonId = safeAddonId(addonId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM addon_state WHERE addon_id = ? AND state_key = ?")) {
            statement.setString(1, safeAddonId);
            statement.setString(2, key);
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
            statement.setString(1, safeAddonId(addonId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear addon state", exception);
        }
    }

    private String safeAddonId(String addonId) {
        return addonId == null || addonId.isBlank() ? "unknown-addon" : addonId;
    }
}
