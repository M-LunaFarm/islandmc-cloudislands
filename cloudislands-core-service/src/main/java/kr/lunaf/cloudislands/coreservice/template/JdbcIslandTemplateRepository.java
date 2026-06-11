package kr.lunaf.cloudislands.coreservice.template;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcIslandTemplateRepository implements IslandTemplateRepository {
    private final DataSource dataSource;

    public JdbcIslandTemplateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<IslandTemplateSnapshot> find(String templateId) {
        String id = normalize(templateId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, display_name, enabled, min_node_version FROM island_templates WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(snapshot(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island template", exception);
        }
    }

    @Override
    public List<IslandTemplateSnapshot> list() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, display_name, enabled, min_node_version FROM island_templates ORDER BY id")) {
            List<IslandTemplateSnapshot> templates = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    templates.add(snapshot(rs));
                }
            }
            return templates;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list island templates", exception);
        }
    }

    @Override
    public IslandTemplateSnapshot upsert(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        String id = normalize(templateId);
        String name = displayName == null || displayName.isBlank() ? id : displayName;
        String version = minNodeVersion == null || minNodeVersion.isBlank() ? null : minNodeVersion.trim();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO island_templates(id, display_name, enabled, min_node_version, updated_at) VALUES (?, ?, ?, ?, now()) ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name, enabled = EXCLUDED.enabled, min_node_version = EXCLUDED.min_node_version, updated_at = now()")) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setBoolean(3, enabled);
            statement.setString(4, version);
            statement.executeUpdate();
            return new IslandTemplateSnapshot(id, name, enabled, version == null ? "" : version);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to upsert island template", exception);
        }
    }

    @Override
    public boolean setEnabled(String templateId, boolean enabled) {
        String id = normalize(templateId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_templates SET enabled = ?, updated_at = now() WHERE id = ?")) {
            statement.setBoolean(1, enabled);
            statement.setString(2, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update island template", exception);
        }
    }

    private static IslandTemplateSnapshot snapshot(ResultSet rs) throws SQLException {
        String minNodeVersion = rs.getString("min_node_version");
        return new IslandTemplateSnapshot(rs.getString("id"), rs.getString("display_name"), rs.getBoolean("enabled"), minNodeVersion == null ? "" : minNodeVersion);
    }

    private static String normalize(String templateId) {
        return templateId == null || templateId.isBlank() ? "default" : templateId.trim().toLowerCase();
    }
}
