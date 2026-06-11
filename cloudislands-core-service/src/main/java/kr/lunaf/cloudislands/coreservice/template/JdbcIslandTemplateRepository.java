package kr.lunaf.cloudislands.coreservice.template;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcIslandTemplateRepository implements IslandTemplateRepository {
    private final DataSource dataSource;

    public JdbcIslandTemplateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<IslandTemplateSnapshot> find(String templateId) {
        String id = templateId == null || templateId.isBlank() ? "default" : templateId;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, display_name, enabled, min_node_version FROM island_templates WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(new IslandTemplateSnapshot(rs.getString("id"), rs.getString("display_name"), rs.getBoolean("enabled"), rs.getString("min_node_version") == null ? "" : rs.getString("min_node_version"))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island template", exception);
        }
    }
}
