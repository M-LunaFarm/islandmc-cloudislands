package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import org.bukkit.block.BlockFace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class MachineRepository {
    private final DatabaseService database;

    MachineRepository(DatabaseService database) {
        this.database = database;
    }

    List<MachineInstance> loadAll() {
        List<MachineInstance> machines = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM machines");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                MachineInstance machine = new MachineInstance(
                        UUID.fromString(rs.getString("machine_id")),
                        UUID.fromString(rs.getString("island_uuid")),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("type_id"),
                        rs.getInt("tier"),
                        new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                );
                machine.direction(BlockFace.valueOf(rs.getString("direction")));
                machine.status(MachineStatus.fromStoredValue(rs.getString("status")));
                machine.inputInventoryId(uuidOrNull(rs.getString("input_inventory_id")));
                machine.outputInventoryId(uuidOrNull(rs.getString("output_inventory_id")));
                machine.powerNetworkId(uuidOrNull(rs.getString("power_network_id")));
                machine.itemNetworkId(uuidOrNull(rs.getString("item_network_id")));
                machine.linkedResourceNodeId(uuidOrNull(rs.getString("linked_resource_node_id")));
                machine.configJson(rs.getString("config_json"));
                machine.selectedRecipeId(selectedRecipeId(machine.configJson()));
                machine.lastProcessAt(rs.getLong("last_process_at"));
                machine.wear(rs.getDouble("wear"));
                machine.createdAt(rs.getLong("created_at"));
                machine.updatedAt(rs.getLong("updated_at"));
                machines.add(machine);
            }
            return machines;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load machines", exception);
        }
    }

    void save(MachineInstance machine) {
        long now = Instant.now().toEpochMilli();
        if (machine.createdAt() <= 0) {
            machine.createdAt(now);
        }
        machine.updatedAt(now);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(saveMachineSql())) {
            statement.setString(1, machine.machineId().toString());
            statement.setString(2, machine.islandUuid().toString());
            statement.setString(3, machine.ownerUuid().toString());
            statement.setString(4, machine.typeId());
            statement.setInt(5, machine.tier());
            statement.setString(6, machine.location().world());
            statement.setInt(7, machine.location().x());
            statement.setInt(8, machine.location().y());
            statement.setInt(9, machine.location().z());
            statement.setString(10, machine.direction().name());
            statement.setString(11, machine.status().name());
            statement.setString(12, stringOrNull(machine.inputInventoryId()));
            statement.setString(13, stringOrNull(machine.outputInventoryId()));
            statement.setString(14, stringOrNull(machine.powerNetworkId()));
            statement.setString(15, stringOrNull(machine.itemNetworkId()));
            statement.setString(16, stringOrNull(machine.linkedResourceNodeId()));
            statement.setLong(17, machine.lastProcessAt());
            statement.setDouble(18, machine.wear());
            statement.setString(19, machineConfigJson(machine));
            statement.setLong(20, machine.createdAt());
            statement.setLong(21, machine.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save machine", exception);
        }
    }

    void delete(UUID machineId) {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement links = connection.prepareStatement("DELETE FROM machine_network_links WHERE machine_id = ?")) {
                links.setString(1, machineId.toString());
                links.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM machines WHERE machine_id = ?")) {
                statement.setString(1, machineId.toString());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete machine", exception);
        }
    }

    void deleteBundle(UUID machineId, Collection<UUID> inventoryIds) {
        try (Connection connection = database.connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteInventoryRows(connection, inventoryIds);
                try (PreparedStatement links = connection.prepareStatement("DELETE FROM machine_network_links WHERE machine_id = ?")) {
                    links.setString(1, machineId.toString());
                    links.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM machines WHERE machine_id = ?")) {
                    statement.setString(1, machineId.toString());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete machine bundle", exception);
        }
    }

    private void deleteInventoryRows(Connection connection, Collection<UUID> inventoryIds) throws SQLException {
        Collection<UUID> safeIds = inventoryIds == null ? List.of() : inventoryIds;
        try (PreparedStatement items = connection.prepareStatement("DELETE FROM virtual_inventory_items WHERE inventory_id = ?");
             PreparedStatement inventories = connection.prepareStatement("DELETE FROM virtual_inventories WHERE inventory_id = ?")) {
            for (UUID inventoryId : safeIds) {
                if (inventoryId == null) {
                    continue;
                }
                items.setString(1, inventoryId.toString());
                items.addBatch();
                inventories.setString(1, inventoryId.toString());
                inventories.addBatch();
            }
            items.executeBatch();
            inventories.executeBatch();
        }
    }

    private String saveMachineSql() {
        if (database.usesMysqlDialect()) {
            return """
                     INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction, status,
                       input_inventory_id, output_inventory_id, power_network_id, item_network_id, linked_resource_node_id,
                       last_process_at, wear, config_json, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z),
                       direction=VALUES(direction), status=VALUES(status), input_inventory_id=VALUES(input_inventory_id),
                       output_inventory_id=VALUES(output_inventory_id), power_network_id=VALUES(power_network_id),
                       item_network_id=VALUES(item_network_id), linked_resource_node_id=VALUES(linked_resource_node_id),
                       last_process_at=VALUES(last_process_at), wear=VALUES(wear), config_json=VALUES(config_json),
                       updated_at=VALUES(updated_at)
                    """;
        }
        return """
                     INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction, status,
                       input_inventory_id, output_inventory_id, power_network_id, item_network_id, linked_resource_node_id,
                       last_process_at, wear, config_json, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(machine_id) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                       direction=excluded.direction, status=excluded.status, input_inventory_id=excluded.input_inventory_id,
                       output_inventory_id=excluded.output_inventory_id, power_network_id=excluded.power_network_id,
                       item_network_id=excluded.item_network_id, linked_resource_node_id=excluded.linked_resource_node_id,
                       last_process_at=excluded.last_process_at, wear=excluded.wear, config_json=excluded.config_json, updated_at=excluded.updated_at
                    """;
    }

    private String selectedRecipeId(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String key = "\"selectedRecipe\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = start + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaped) {
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                String parsed = value.toString();
                return parsed.isBlank() ? null : parsed;
            } else {
                value.append(current);
            }
        }
        return null;
    }

    private String machineConfigJson(MachineInstance machine) {
        String base = validJsonObject(machine.configJson()) ? machine.configJson().trim() : "{}";
        String selectedRecipe = machine.selectedRecipeId();
        String withoutSelectedRecipe = removeTopLevelStringField(base, "selectedRecipe");
        if (selectedRecipe == null || selectedRecipe.isBlank()) {
            return withoutSelectedRecipe;
        }
        String field = "\"selectedRecipe\":\"" + selectedRecipe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (withoutSelectedRecipe.equals("{}")) {
            return "{" + field + "}";
        }
        return withoutSelectedRecipe.substring(0, withoutSelectedRecipe.length() - 1) + "," + field + "}";
    }

    private boolean validJsonObject(String json) {
        if (json == null) {
            return false;
        }
        String trimmed = json.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private String removeTopLevelStringField(String json, String fieldName) {
        String trimmed = json.trim();
        if (trimmed.equals("{}")) {
            return "{}";
        }
        List<String> fields = splitTopLevelFields(trimmed.substring(1, trimmed.length() - 1));
        String prefix = "\"" + fieldName + "\"";
        List<String> kept = fields.stream()
                .filter(field -> !field.trim().startsWith(prefix))
                .toList();
        return kept.isEmpty() ? "{}" : kept.stream().collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private List<String> splitTopLevelFields(String body) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = 0; index < body.length(); index++) {
            char value = body.charAt(index);
            if (escaped) {
                current.append(value);
                escaped = false;
                continue;
            }
            if (value == '\\') {
                current.append(value);
                escaped = true;
                continue;
            }
            if (value == '"') {
                quoted = !quoted;
            } else if (!quoted && (value == '{' || value == '[')) {
                depth++;
            } else if (!quoted && (value == '}' || value == ']')) {
                depth = Math.max(0, depth - 1);
            } else if (!quoted && depth == 0 && value == ',') {
                String field = current.toString().trim();
                if (!field.isEmpty()) {
                    fields.add(field);
                }
                current.setLength(0);
                continue;
            }
            current.append(value);
        }
        String field = current.toString().trim();
        if (!field.isEmpty()) {
            fields.add(field);
        }
        return fields;
    }

    private UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private String stringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }
}
