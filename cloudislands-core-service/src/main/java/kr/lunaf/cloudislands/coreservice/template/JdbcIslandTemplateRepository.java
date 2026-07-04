package kr.lunaf.cloudislands.coreservice.template;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
             PreparedStatement statement = connection.prepareStatement(selectTemplateSql() + " WHERE id = ?")) {
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
             PreparedStatement statement = connection.prepareStatement(selectTemplateSql() + " ORDER BY sort_order, id")) {
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
    public IslandTemplateSnapshot upsert(IslandTemplateSnapshot template) {
        IslandTemplateSnapshot snapshot = template == null ? new IslandTemplateSnapshot("default", "Default Island", true, "") : template;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertTemplateSql(connection))) {
            bindTemplate(statement, snapshot);
            statement.executeUpdate();
            return find(snapshot.id()).orElse(snapshot);
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

    @Override
    public boolean delete(String templateId) {
        String id = normalize(templateId);
        if ("default".equals(id) || "superiorskyblock2".equals(id)) {
            return false;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM island_templates WHERE id = ?")) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to delete island template", exception);
        }
    }

    @Override
    public boolean reorder(String templateId, int sortOrder) {
        String id = normalize(templateId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE island_templates SET sort_order = ?, updated_at = now() WHERE id = ?")) {
            statement.setInt(1, Math.max(0, sortOrder));
            statement.setString(2, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to reorder island template", exception);
        }
    }

    private static IslandTemplateSnapshot snapshot(ResultSet rs) throws SQLException {
        String minNodeVersion = rs.getString("min_node_version");
        return new IslandTemplateSnapshot(
            rs.getString("id"),
            rs.getString("display_name"),
            text(rs, "description"),
            text(rs, "category", "default"),
            rs.getBoolean("enabled"),
            minNodeVersion == null ? "" : minNodeVersion,
            text(rs, "required_permission"),
            text(rs, "icon_material", "GRASS_BLOCK"),
            rs.getInt("icon_custom_model_data"),
            text(rs, "preview_image_key"),
            text(rs, "bundle_storage_path"),
            text(rs, "bundle_checksum"),
            rs.getLong("bundle_size_bytes"),
            rs.getInt("schema_version"),
            rs.getInt("default_island_size"),
            rs.getDouble("spawn_world_offset_x"),
            rs.getDouble("spawn_world_offset_y"),
            rs.getDouble("spawn_world_offset_z"),
            rs.getFloat("spawn_yaw"),
            rs.getFloat("spawn_pitch"),
            text(rs, "home_name", "default"),
            text(rs, "environment_preset", "normal"),
            text(rs, "biome_key", "minecraft:plains"),
            text(rs, "border_color", "BLUE"),
            text(rs, "bank_initial_balance", "0"),
            text(rs, "creation_cost", "0"),
            rs.getInt("sort_order"),
            tags(text(rs, "tags_csv")),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private static String normalize(String templateId) {
        return templateId == null || templateId.isBlank() ? "default" : templateId.trim().toLowerCase();
    }

    private String upsertTemplateSql(Connection connection) throws SQLException {
        String columns = "id, display_name, description, category, enabled, min_node_version, required_permission, icon_material, icon_custom_model_data, preview_image_key, bundle_storage_path, bundle_checksum, bundle_size_bytes, schema_version, default_island_size, spawn_world_offset_x, spawn_world_offset_y, spawn_world_offset_z, spawn_yaw, spawn_pitch, home_name, environment_preset, biome_key, border_color, bank_initial_balance, creation_cost, sort_order, tags_csv, updated_at";
        String values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()";
        if (mysqlLike(connection)) {
            return "INSERT INTO island_templates(" + columns + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), description = VALUES(description), category = VALUES(category), enabled = VALUES(enabled), min_node_version = VALUES(min_node_version), required_permission = VALUES(required_permission), icon_material = VALUES(icon_material), icon_custom_model_data = VALUES(icon_custom_model_data), preview_image_key = VALUES(preview_image_key), bundle_storage_path = VALUES(bundle_storage_path), bundle_checksum = VALUES(bundle_checksum), bundle_size_bytes = VALUES(bundle_size_bytes), schema_version = VALUES(schema_version), default_island_size = VALUES(default_island_size), spawn_world_offset_x = VALUES(spawn_world_offset_x), spawn_world_offset_y = VALUES(spawn_world_offset_y), spawn_world_offset_z = VALUES(spawn_world_offset_z), spawn_yaw = VALUES(spawn_yaw), spawn_pitch = VALUES(spawn_pitch), home_name = VALUES(home_name), environment_preset = VALUES(environment_preset), biome_key = VALUES(biome_key), border_color = VALUES(border_color), bank_initial_balance = VALUES(bank_initial_balance), creation_cost = VALUES(creation_cost), sort_order = VALUES(sort_order), tags_csv = VALUES(tags_csv), updated_at = now()";
        }
        return "INSERT INTO island_templates(" + columns + ") VALUES (" + values + ") ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name, description = EXCLUDED.description, category = EXCLUDED.category, enabled = EXCLUDED.enabled, min_node_version = EXCLUDED.min_node_version, required_permission = EXCLUDED.required_permission, icon_material = EXCLUDED.icon_material, icon_custom_model_data = EXCLUDED.icon_custom_model_data, preview_image_key = EXCLUDED.preview_image_key, bundle_storage_path = EXCLUDED.bundle_storage_path, bundle_checksum = EXCLUDED.bundle_checksum, bundle_size_bytes = EXCLUDED.bundle_size_bytes, schema_version = EXCLUDED.schema_version, default_island_size = EXCLUDED.default_island_size, spawn_world_offset_x = EXCLUDED.spawn_world_offset_x, spawn_world_offset_y = EXCLUDED.spawn_world_offset_y, spawn_world_offset_z = EXCLUDED.spawn_world_offset_z, spawn_yaw = EXCLUDED.spawn_yaw, spawn_pitch = EXCLUDED.spawn_pitch, home_name = EXCLUDED.home_name, environment_preset = EXCLUDED.environment_preset, biome_key = EXCLUDED.biome_key, border_color = EXCLUDED.border_color, bank_initial_balance = EXCLUDED.bank_initial_balance, creation_cost = EXCLUDED.creation_cost, sort_order = EXCLUDED.sort_order, tags_csv = EXCLUDED.tags_csv, updated_at = now()";
    }

    private static String selectTemplateSql() {
        return "SELECT id, display_name, description, category, enabled, min_node_version, required_permission, icon_material, icon_custom_model_data, preview_image_key, bundle_storage_path, bundle_checksum, bundle_size_bytes, schema_version, default_island_size, spawn_world_offset_x, spawn_world_offset_y, spawn_world_offset_z, spawn_yaw, spawn_pitch, home_name, environment_preset, biome_key, border_color, bank_initial_balance, creation_cost, sort_order, tags_csv, created_at, updated_at FROM island_templates";
    }

    private static void bindTemplate(PreparedStatement statement, IslandTemplateSnapshot template) throws SQLException {
        statement.setString(1, template.id());
        statement.setString(2, template.displayName());
        statement.setString(3, template.description());
        statement.setString(4, template.category());
        statement.setBoolean(5, template.enabled());
        statement.setString(6, nullable(template.minNodeVersion()));
        statement.setString(7, template.requiredPermission());
        statement.setString(8, template.iconMaterial());
        statement.setInt(9, template.iconCustomModelData());
        statement.setString(10, template.previewImageKey());
        statement.setString(11, template.bundleStoragePath());
        statement.setString(12, template.bundleChecksum());
        statement.setLong(13, template.bundleSizeBytes());
        statement.setInt(14, template.schemaVersion());
        statement.setInt(15, template.defaultIslandSize());
        statement.setDouble(16, template.spawnWorldOffsetX());
        statement.setDouble(17, template.spawnWorldOffsetY());
        statement.setDouble(18, template.spawnWorldOffsetZ());
        statement.setFloat(19, template.spawnYaw());
        statement.setFloat(20, template.spawnPitch());
        statement.setString(21, template.homeName());
        statement.setString(22, template.environmentPreset());
        statement.setString(23, template.biomeKey());
        statement.setString(24, template.borderColor());
        statement.setString(25, template.bankInitialBalance());
        statement.setString(26, template.creationCost());
        statement.setInt(27, template.sortOrder());
        statement.setString(28, String.join(",", template.tags()));
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(ResultSet rs, String column) throws SQLException {
        return text(rs, column, "");
    }

    private static String text(ResultSet rs, String column, String fallback) throws SQLException {
        String value = rs.getString(column);
        return value == null ? fallback : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private static List<String> tags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(tag -> !tag.isBlank())
            .toList();
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return productName.contains("mysql") || productName.contains("mariadb");
    }
}
