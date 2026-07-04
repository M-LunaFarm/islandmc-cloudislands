package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class FactoryIslandRepository {
    private final DatabaseService database;

    FactoryIslandRepository(DatabaseService database) {
        this.database = database;
    }

    Optional<FactoryIsland> find(UUID islandUuid) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM factory_islands WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapIsland(rs));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load factory island", exception);
        }
    }

    List<FactoryIsland> loadAll() {
        List<FactoryIsland> islands = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM factory_islands");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                islands.add(mapIsland(rs));
            }
            return islands;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load factory islands", exception);
        }
    }

    void save(FactoryIsland island) {
        long now = Instant.now().toEpochMilli();
        if (island.createdAt() <= 0) {
            island.createdAt(now);
        }
        island.updatedAt(now);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(saveIslandSql())) {
            statement.setString(1, island.islandUuid().toString());
            statement.setString(2, island.ownerUuid().toString());
            statement.setInt(3, island.tier());
            statement.setLong(4, island.researchPoints());
            statement.setLong(5, island.reputation());
            statement.setLong(6, island.maintenanceDebt());
            statement.setString(7, island.maintenanceStatus().name());
            statement.setLong(8, island.factoryScore());
            statement.setLong(9, island.lastMaintenanceAt());
            statement.setLong(10, island.lastTickAt());
            statement.setInt(11, island.emergencyContractsUsedToday());
            statement.setString(12, island.activeWorld());
            statement.setInt(13, island.activeCenterX());
            statement.setInt(14, island.activeCenterY());
            statement.setInt(15, island.activeCenterZ());
            statement.setString(16, island.pendingMachineRemapWorld());
            statement.setInt(17, island.pendingMachineRemapCenterX());
            statement.setInt(18, island.pendingMachineRemapCenterY());
            statement.setInt(19, island.pendingMachineRemapCenterZ());
            statement.setString(20, island.pendingResourceNodeRemapWorld());
            statement.setInt(21, island.pendingResourceNodeRemapCenterX());
            statement.setInt(22, island.pendingResourceNodeRemapCenterY());
            statement.setInt(23, island.pendingResourceNodeRemapCenterZ());
            statement.setLong(24, island.createdAt());
            statement.setLong(25, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save factory island", exception);
        }
    }

    private FactoryIsland mapIsland(ResultSet rs) throws SQLException {
        FactoryIsland island = new FactoryIsland(UUID.fromString(rs.getString("island_uuid")), UUID.fromString(rs.getString("owner_uuid")));
        island.tier(rs.getInt("tier"));
        island.researchPoints(rs.getLong("research_points"));
        island.reputation(rs.getLong("reputation"));
        island.maintenanceDebt(rs.getLong("maintenance_debt"));
        island.maintenanceStatus(MaintenanceStatus.valueOf(rs.getString("maintenance_status")));
        island.factoryScore(rs.getLong("factory_score"));
        island.lastMaintenanceAt(rs.getLong("last_maintenance_at"));
        island.lastTickAt(rs.getLong("last_tick_at"));
        island.createdAt(rs.getLong("created_at"));
        island.updatedAt(rs.getLong("updated_at"));
        island.emergencyContractsUsedToday(rs.getInt("emergency_contracts_used_today"));
        island.activeWorld(rs.getString("active_world"));
        island.activeCenterX(rs.getInt("active_center_x"));
        island.activeCenterY(rs.getInt("active_center_y"));
        island.activeCenterZ(rs.getInt("active_center_z"));
        island.pendingMachineRemap(rs.getString("pending_machine_remap_world"), rs.getInt("pending_machine_remap_center_x"), rs.getInt("pending_machine_remap_center_y"), rs.getInt("pending_machine_remap_center_z"));
        island.pendingResourceNodeRemap(rs.getString("pending_resource_node_remap_world"), rs.getInt("pending_resource_node_remap_center_x"), rs.getInt("pending_resource_node_remap_center_y"), rs.getInt("pending_resource_node_remap_center_z"));
        return island;
    }

    private String saveIslandSql() {
        if (database.usesMysqlDialect()) {
            return """
                     INSERT INTO factory_islands(island_uuid, owner_uuid, tier, research_points, reputation, maintenance_debt,
                       maintenance_status, factory_score, last_maintenance_at, last_tick_at, emergency_contracts_used_today,
                       active_world, active_center_x, active_center_y, active_center_z,
                       pending_machine_remap_world, pending_machine_remap_center_x, pending_machine_remap_center_y, pending_machine_remap_center_z,
                       pending_resource_node_remap_world, pending_resource_node_remap_center_x, pending_resource_node_remap_center_y, pending_resource_node_remap_center_z,
                       created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE owner_uuid=VALUES(owner_uuid), tier=VALUES(tier),
                       research_points=VALUES(research_points), reputation=VALUES(reputation),
                       maintenance_debt=VALUES(maintenance_debt), maintenance_status=VALUES(maintenance_status),
                       factory_score=VALUES(factory_score), last_maintenance_at=VALUES(last_maintenance_at),
                       last_tick_at=VALUES(last_tick_at), emergency_contracts_used_today=VALUES(emergency_contracts_used_today),
                       active_world=VALUES(active_world), active_center_x=VALUES(active_center_x),
                       active_center_y=VALUES(active_center_y), active_center_z=VALUES(active_center_z),
                       pending_machine_remap_world=VALUES(pending_machine_remap_world),
                       pending_machine_remap_center_x=VALUES(pending_machine_remap_center_x),
                       pending_machine_remap_center_y=VALUES(pending_machine_remap_center_y),
                       pending_machine_remap_center_z=VALUES(pending_machine_remap_center_z),
                       pending_resource_node_remap_world=VALUES(pending_resource_node_remap_world),
                       pending_resource_node_remap_center_x=VALUES(pending_resource_node_remap_center_x),
                       pending_resource_node_remap_center_y=VALUES(pending_resource_node_remap_center_y),
                       pending_resource_node_remap_center_z=VALUES(pending_resource_node_remap_center_z),
                       updated_at=VALUES(updated_at)
                    """;
        }
        return """
                     INSERT INTO factory_islands(island_uuid, owner_uuid, tier, research_points, reputation, maintenance_debt,
                       maintenance_status, factory_score, last_maintenance_at, last_tick_at, emergency_contracts_used_today,
                       active_world, active_center_x, active_center_y, active_center_z,
                       pending_machine_remap_world, pending_machine_remap_center_x, pending_machine_remap_center_y, pending_machine_remap_center_z,
                       pending_resource_node_remap_world, pending_resource_node_remap_center_x, pending_resource_node_remap_center_y, pending_resource_node_remap_center_z,
                       created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(island_uuid) DO UPDATE SET owner_uuid=excluded.owner_uuid, tier=excluded.tier,
                       research_points=excluded.research_points, reputation=excluded.reputation,
                       maintenance_debt=excluded.maintenance_debt, maintenance_status=excluded.maintenance_status,
                       factory_score=excluded.factory_score, last_maintenance_at=excluded.last_maintenance_at,
                       last_tick_at=excluded.last_tick_at, emergency_contracts_used_today=excluded.emergency_contracts_used_today,
                       active_world=excluded.active_world, active_center_x=excluded.active_center_x,
                       active_center_y=excluded.active_center_y, active_center_z=excluded.active_center_z,
                       pending_machine_remap_world=excluded.pending_machine_remap_world,
                       pending_machine_remap_center_x=excluded.pending_machine_remap_center_x,
                       pending_machine_remap_center_y=excluded.pending_machine_remap_center_y,
                       pending_machine_remap_center_z=excluded.pending_machine_remap_center_z,
                       pending_resource_node_remap_world=excluded.pending_resource_node_remap_world,
                       pending_resource_node_remap_center_x=excluded.pending_resource_node_remap_center_x,
                       pending_resource_node_remap_center_y=excluded.pending_resource_node_remap_center_y,
                       pending_resource_node_remap_center_z=excluded.pending_resource_node_remap_center_z,
                       updated_at=excluded.updated_at
                    """;
    }
}
