package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.storage.VirtualInventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class VirtualInventoryRepository {
    private final DatabaseService database;

    VirtualInventoryRepository(DatabaseService database) {
        this.database = database;
    }

    void save(VirtualInventory inventory) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement inv = connection.prepareStatement(saveInventorySql())) {
                inv.setString(1, inventory.inventoryId().toString());
                inv.setString(2, inventory.islandUuid().toString());
                inv.setString(3, inventory.holderType());
                inv.setString(4, inventory.holderId());
                inv.setLong(5, inventory.capacity());
                inv.setLong(6, now);
                inv.setLong(7, now);
                inv.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM virtual_inventory_items WHERE inventory_id = ?")) {
                delete.setString(1, inventory.inventoryId().toString());
                delete.executeUpdate();
            }
            try (PreparedStatement item = connection.prepareStatement("INSERT INTO virtual_inventory_items(inventory_id, item_id, amount) VALUES(?, ?, ?)")) {
                for (var entry : inventory.items().entrySet()) {
                    item.setString(1, inventory.inventoryId().toString());
                    item.setString(2, entry.getKey());
                    item.setLong(3, entry.getValue());
                    item.addBatch();
                }
                item.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save inventory", exception);
        }
    }

    Optional<VirtualInventory> load(UUID inventoryId) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM virtual_inventories WHERE inventory_id = ?")) {
            statement.setString(1, inventoryId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                VirtualInventory inventory = new VirtualInventory(
                        inventoryId,
                        UUID.fromString(rs.getString("island_uuid")),
                        rs.getString("holder_type"),
                        rs.getString("holder_id"),
                        rs.getLong("capacity")
                );
                loadInventoryItems(connection, inventory);
                return Optional.of(inventory);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load inventory", exception);
        }
    }

    Optional<VirtualInventory> findByHolder(UUID islandUuid, String holderType, String holderId) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT inventory_id FROM virtual_inventories
                     WHERE island_uuid = ? AND holder_type = ? AND holder_id = ?
                     LIMIT 1
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, holderType);
            statement.setString(3, holderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return load(UUID.fromString(rs.getString("inventory_id")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find inventory", exception);
        }
    }

    void delete(UUID inventoryId) {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement items = connection.prepareStatement("DELETE FROM virtual_inventory_items WHERE inventory_id = ?")) {
                items.setString(1, inventoryId.toString());
                items.executeUpdate();
            }
            try (PreparedStatement inventory = connection.prepareStatement("DELETE FROM virtual_inventories WHERE inventory_id = ?")) {
                inventory.setString(1, inventoryId.toString());
                inventory.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete inventory", exception);
        }
    }

    List<UUID> inventoryIds(UUID islandUuid) {
        List<UUID> ids = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT inventory_id FROM virtual_inventories WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(UUID.fromString(rs.getString("inventory_id")));
                }
            }
            return ids;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list inventory ids", exception);
        }
    }

    private String saveInventorySql() {
        if (database.usesMysqlDialect()) {
            return """
                    INSERT INTO virtual_inventories(inventory_id, island_uuid, holder_type, holder_id, capacity, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE capacity=VALUES(capacity), updated_at=VALUES(updated_at)
                    """;
        }
        return """
                    INSERT INTO virtual_inventories(inventory_id, island_uuid, holder_type, holder_id, capacity, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(inventory_id) DO UPDATE SET capacity=excluded.capacity, updated_at=excluded.updated_at
                    """;
    }

    private void loadInventoryItems(Connection connection, VirtualInventory inventory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT item_id, amount FROM virtual_inventory_items WHERE inventory_id = ?")) {
            statement.setString(1, inventory.inventoryId().toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    inventory.set(rs.getString("item_id"), rs.getLong("amount"));
                }
            }
        }
    }
}
