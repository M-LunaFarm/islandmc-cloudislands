package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.PowerNetwork;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class NetworkRepository {
    private final DatabaseService database;

    NetworkRepository(DatabaseService database) {
        this.database = database;
    }

    void replaceItemNetworks(UUID islandUuid, List<ItemNetwork> networks) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteLinks = connection.prepareStatement("""
                    DELETE FROM machine_network_links
                    WHERE network_type = 'ITEM'
                      AND network_id IN (SELECT network_id FROM item_networks WHERE island_uuid = ?)
                    """)) {
                deleteLinks.setString(1, islandUuid.toString());
                deleteLinks.executeUpdate();
            }
            try (PreparedStatement deleteNetworks = connection.prepareStatement("DELETE FROM item_networks WHERE island_uuid = ?")) {
                deleteNetworks.setString(1, islandUuid.toString());
                deleteNetworks.executeUpdate();
            }
            try (PreparedStatement clearMachines = connection.prepareStatement("UPDATE machines SET item_network_id = NULL WHERE island_uuid = ?")) {
                clearMachines.setString(1, islandUuid.toString());
                clearMachines.executeUpdate();
            }
            try (PreparedStatement networkStatement = connection.prepareStatement("""
                    INSERT INTO item_networks(network_id, island_uuid, throughput_per_minute, buffer_inventory_id, dirty, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?)
                    """);
                 PreparedStatement linkStatement = connection.prepareStatement("""
                    INSERT INTO machine_network_links(machine_id, network_id, network_type)
                    VALUES(?, ?, 'ITEM')
                    """);
                 PreparedStatement machineStatement = connection.prepareStatement("""
                    UPDATE machines SET item_network_id = ?, updated_at = ? WHERE machine_id = ?
                    """)) {
                for (ItemNetwork network : networks) {
                    networkStatement.setString(1, network.networkId().toString());
                    networkStatement.setString(2, network.islandUuid().toString());
                    networkStatement.setInt(3, network.throughputPerMinute());
                    networkStatement.setString(4, stringOrNull(network.bufferInventoryId()));
                    networkStatement.setInt(5, network.dirty() ? 1 : 0);
                    networkStatement.setLong(6, now);
                    networkStatement.addBatch();
                    for (UUID machineId : network.connectedMachineIds()) {
                        linkStatement.setString(1, machineId.toString());
                        linkStatement.setString(2, network.networkId().toString());
                        linkStatement.addBatch();
                        machineStatement.setString(1, network.networkId().toString());
                        machineStatement.setLong(2, now);
                        machineStatement.setString(3, machineId.toString());
                        machineStatement.addBatch();
                    }
                }
                networkStatement.executeBatch();
                linkStatement.executeBatch();
                machineStatement.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace item networks", exception);
        }
    }

    List<ItemNetwork> loadItemNetworks(UUID islandUuid) {
        List<ItemNetwork> networks = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM item_networks WHERE island_uuid = ? ORDER BY network_id
                     """)) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID networkId = UUID.fromString(rs.getString("network_id"));
                    networks.add(new ItemNetwork(
                            networkId,
                            islandUuid,
                            rs.getInt("throughput_per_minute"),
                            uuidOrNull(rs.getString("buffer_inventory_id")),
                            rs.getInt("dirty") != 0,
                            rs.getLong("updated_at"),
                            loadNetworkMachineIds(connection, networkId, "ITEM"),
                            itemRoutes(connection, networkId)
                    ));
                }
            }
            return networks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load item networks", exception);
        }
    }

    void replacePowerNetworks(UUID islandUuid, List<PowerNetwork> networks) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteLinks = connection.prepareStatement("""
                    DELETE FROM machine_network_links
                    WHERE network_type = 'POWER'
                      AND network_id IN (SELECT network_id FROM power_networks WHERE island_uuid = ?)
                    """)) {
                deleteLinks.setString(1, islandUuid.toString());
                deleteLinks.executeUpdate();
            }
            try (PreparedStatement deleteNetworks = connection.prepareStatement("DELETE FROM power_networks WHERE island_uuid = ?")) {
                deleteNetworks.setString(1, islandUuid.toString());
                deleteNetworks.executeUpdate();
            }
            try (PreparedStatement clearMachines = connection.prepareStatement("UPDATE machines SET power_network_id = NULL WHERE island_uuid = ?")) {
                clearMachines.setString(1, islandUuid.toString());
                clearMachines.executeUpdate();
            }
            try (PreparedStatement networkStatement = connection.prepareStatement("""
                    INSERT INTO power_networks(network_id, island_uuid, generation_per_second, consumption_per_second,
                      battery_stored, battery_capacity, power_ratio, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                    """);
                 PreparedStatement linkStatement = connection.prepareStatement("""
                    INSERT INTO machine_network_links(machine_id, network_id, network_type)
                    VALUES(?, ?, 'POWER')
                    """);
                 PreparedStatement machineStatement = connection.prepareStatement("""
                    UPDATE machines SET power_network_id = ?, updated_at = ? WHERE machine_id = ?
                    """)) {
                for (PowerNetwork network : networks) {
                    networkStatement.setString(1, network.networkId().toString());
                    networkStatement.setString(2, network.islandUuid().toString());
                    networkStatement.setDouble(3, network.generationPerSecond());
                    networkStatement.setDouble(4, network.consumptionPerSecond());
                    networkStatement.setDouble(5, network.batteryStored());
                    networkStatement.setDouble(6, network.batteryCapacity());
                    networkStatement.setDouble(7, network.powerRatio());
                    networkStatement.setLong(8, now);
                    networkStatement.addBatch();
                    for (UUID machineId : network.connectedMachineIds()) {
                        linkStatement.setString(1, machineId.toString());
                        linkStatement.setString(2, network.networkId().toString());
                        linkStatement.addBatch();
                        machineStatement.setString(1, network.networkId().toString());
                        machineStatement.setLong(2, now);
                        machineStatement.setString(3, machineId.toString());
                        machineStatement.addBatch();
                    }
                }
                networkStatement.executeBatch();
                linkStatement.executeBatch();
                machineStatement.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace power networks", exception);
        }
    }

    List<PowerNetwork> loadPowerNetworks(UUID islandUuid) {
        List<PowerNetwork> networks = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM power_networks WHERE island_uuid = ? ORDER BY network_id
                     """)) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID networkId = UUID.fromString(rs.getString("network_id"));
                    networks.add(new PowerNetwork(
                            networkId,
                            islandUuid,
                            rs.getDouble("generation_per_second"),
                            rs.getDouble("consumption_per_second"),
                            rs.getDouble("battery_stored"),
                            rs.getDouble("battery_capacity"),
                            rs.getDouble("power_ratio"),
                            rs.getLong("updated_at"),
                            loadNetworkMachineIds(connection, networkId, "POWER")
                    ));
                }
            }
            return networks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load power networks", exception);
        }
    }

    private Set<UUID> loadNetworkMachineIds(Connection connection, UUID networkId, String networkType) throws SQLException {
        Set<UUID> machineIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT machine_id FROM machine_network_links WHERE network_id = ? AND network_type = ?
                """)) {
            statement.setString(1, networkId.toString());
            statement.setString(2, networkType);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    machineIds.add(UUID.fromString(rs.getString("machine_id")));
                }
            }
        }
        return machineIds;
    }

    private List<ItemNetwork.Route> itemRoutes(Connection connection, UUID networkId) throws SQLException {
        UUID bufferOwnerMachineId = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT machine_id FROM machines
                WHERE item_network_id = ? AND input_inventory_id = (
                  SELECT buffer_inventory_id FROM item_networks WHERE network_id = ?
                )
                LIMIT 1
                """)) {
            statement.setString(1, networkId.toString());
            statement.setString(2, networkId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    bufferOwnerMachineId = UUID.fromString(rs.getString("machine_id"));
                }
            }
        }
        if (bufferOwnerMachineId == null) {
            return List.of();
        }
        UUID root = bufferOwnerMachineId;
        return loadNetworkMachineIds(connection, networkId, "ITEM").stream()
                .filter(machineId -> !machineId.equals(root))
                .map(machineId -> new ItemNetwork.Route(root, machineId))
                .toList();
    }

    private UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private String stringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }
}
