package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisIslandRelocationServiceTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000005001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000005002");
    private static final UUID MACHINE_ID = UUID.fromString("00000000-0000-0000-0000-000000005003");
    private static final UUID NODE_ID = UUID.fromString("00000000-0000-0000-0000-000000005004");

    @TempDir
    Path tempDir;

    @Test
    void relocationRemapsMachinesAndResourceNodesByIslandUuidAcrossNodes() {
        try (DatabaseHandle handle = openDatabase("relocation")) {
            FactoryIsland island = new FactoryIsland(ISLAND_ID, OWNER_ID);
            island.activeWorld("ci_shard_001");
            island.activeCenterX(0);
            island.activeCenterY(64);
            island.activeCenterZ(0);
            handle.database().saveIsland(island);

            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineInstance machine = new MachineInstance(MACHINE_ID, ISLAND_ID, OWNER_ID, "grinder_t1", 1, new BlockKey("ci_shard_001", 4, 65, 8));
            machines.save(machine);

            ResourceNode node = new ResourceNode(NODE_ID, ISLAND_ID, "MINERAL", "iron_ore", 1.0D, 100, 250, 60, 1, new BlockKey("ci_shard_001", -6, 63, 12), 0, 0);
            handle.database().saveNode(node);
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(nodeConfig());

            SatisIslandRelocationService relocation = new SatisIslandRelocationService(machines, nodes);
            SatisIslandRelocationService.RelocationResult result = relocation.relocate(ISLAND_ID, island, "ci_shard_006", 2048, 80, -1024, true, true);
            handle.database().saveIsland(island);

            assertEquals("2048,16,-1024", result.delta());
            assertTrue(result.machinesRemapped());
            assertTrue(result.resourceNodesRemapped());
            assertFalse(result.machineRemapDeferred());
            assertFalse(result.resourceNodeRemapDeferred());

            MachineInstance remappedMachine = machines.find(MACHINE_ID).orElseThrow();
            assertEquals("ci_shard_006", remappedMachine.world());
            assertEquals(2052, remappedMachine.x());
            assertEquals(81, remappedMachine.y());
            assertEquals(-1016, remappedMachine.z());

            ResourceNode remappedNode = nodes.nodes(ISLAND_ID).stream()
                    .filter(candidate -> candidate.nodeId().equals(NODE_ID))
                    .findFirst()
                    .orElseThrow();
            assertEquals("ci_shard_006", remappedNode.world());
            assertEquals(2042, remappedNode.x());
            assertEquals(79, remappedNode.y());
            assertEquals(-1012, remappedNode.z());

            FactoryIsland persistedIsland = handle.database().findIsland(ISLAND_ID).orElseThrow();
            assertEquals("ci_shard_006", persistedIsland.activeWorld());
            assertEquals(2048, persistedIsland.activeCenterX());
            assertEquals(80, persistedIsland.activeCenterY());
            assertEquals(-1024, persistedIsland.activeCenterZ());
        }
    }

    @Test
    void relocationPreservesDataWhenFeaturesAreDisabled() {
        try (DatabaseHandle handle = openDatabase("relocation-disabled")) {
            FactoryIsland island = new FactoryIsland(ISLAND_ID, OWNER_ID);
            island.activeWorld("ci_shard_001");
            island.activeCenterX(0);
            island.activeCenterY(64);
            island.activeCenterZ(0);
            handle.database().saveIsland(island);

            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineInstance machine = new MachineInstance(MACHINE_ID, ISLAND_ID, OWNER_ID, "grinder_t1", 1, new BlockKey("ci_shard_001", 4, 65, 8));
            machines.save(machine);
            ResourceNode node = new ResourceNode(NODE_ID, ISLAND_ID, "MINERAL", "iron_ore", 1.0D, 100, 250, 60, 1, new BlockKey("ci_shard_001", -6, 63, 12), 0, 0);
            handle.database().saveNode(node);
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(nodeConfig());

            SatisIslandRelocationService relocation = new SatisIslandRelocationService(machines, nodes);
            SatisIslandRelocationService.RelocationResult result = relocation.relocate(ISLAND_ID, island, "ci_shard_006", 2048, 80, -1024, false, false);

            assertEquals("2048,16,-1024", result.delta());
            assertFalse(result.machinesRemapped());
            assertFalse(result.resourceNodesRemapped());
            assertTrue(result.machineRemapDeferred());
            assertTrue(result.resourceNodeRemapDeferred());
            assertEquals("when-feature-disabled-store-original-center-and-apply-remap-when-reenabled", result.deferredRemapPolicy());
            assertEquals("ci_shard_001", machines.find(MACHINE_ID).orElseThrow().world());
            assertEquals("ci_shard_001", nodes.nodes(ISLAND_ID).getFirst().world());
            assertEquals("ci_shard_006", island.activeWorld());
            assertTrue(island.hasPendingMachineRemap());
            assertTrue(island.hasPendingResourceNodeRemap());
            assertEquals("ci_shard_001", island.pendingMachineRemapWorld());
            assertEquals("ci_shard_001", island.pendingResourceNodeRemapWorld());
        }
    }

    @Test
    void deferredRelocationAppliesWhenFeaturesAreReEnabled() {
        try (DatabaseHandle handle = openDatabase("relocation-reenable")) {
            FactoryIsland island = new FactoryIsland(ISLAND_ID, OWNER_ID);
            island.activeWorld("ci_shard_001");
            island.activeCenterX(0);
            island.activeCenterY(64);
            island.activeCenterZ(0);
            handle.database().saveIsland(island);

            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineInstance machine = new MachineInstance(MACHINE_ID, ISLAND_ID, OWNER_ID, "grinder_t1", 1, new BlockKey("ci_shard_001", 4, 65, 8));
            machines.save(machine);
            ResourceNode node = new ResourceNode(NODE_ID, ISLAND_ID, "MINERAL", "iron_ore", 1.0D, 100, 250, 60, 1, new BlockKey("ci_shard_001", -6, 63, 12), 0, 0);
            handle.database().saveNode(node);
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(nodeConfig());

            SatisIslandRelocationService relocation = new SatisIslandRelocationService(machines, nodes);
            relocation.relocate(ISLAND_ID, island, "ci_shard_006", 2048, 80, -1024, false, false);
            handle.database().saveIsland(island);

            FactoryIsland reloaded = handle.database().findIsland(ISLAND_ID).orElseThrow();
            SatisIslandRelocationService.RelocationResult result = relocation.relocate(ISLAND_ID, reloaded, "ci_shard_006", 2048, 80, -1024, true, true);
            handle.database().saveIsland(reloaded);

            assertEquals("0,0,0", result.delta());
            assertEquals("2048,16,-1024", result.machineDelta());
            assertEquals("2048,16,-1024", result.resourceNodeDelta());
            assertTrue(result.machinesRemapped());
            assertTrue(result.resourceNodesRemapped());
            assertFalse(result.machineRemapDeferred());
            assertFalse(result.resourceNodeRemapDeferred());
            assertFalse(reloaded.hasPendingMachineRemap());
            assertFalse(reloaded.hasPendingResourceNodeRemap());
            assertEquals("ci_shard_006", machines.find(MACHINE_ID).orElseThrow().world());
            assertEquals(2052, machines.find(MACHINE_ID).orElseThrow().x());
            assertEquals("ci_shard_006", nodes.nodes(ISLAND_ID).getFirst().world());
            assertEquals(2042, nodes.nodes(ISLAND_ID).getFirst().x());
        }
    }

    @Test
    void disabledFeatureDoesNotCreatePendingRemapWhenPlacementDidNotChange() {
        try (DatabaseHandle handle = openDatabase("relocation-noop-disabled")) {
            FactoryIsland island = new FactoryIsland(ISLAND_ID, OWNER_ID);
            island.activeWorld("ci_shard_006");
            island.activeCenterX(2048);
            island.activeCenterY(80);
            island.activeCenterZ(-1024);
            handle.database().saveIsland(island);

            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineInstance machine = new MachineInstance(MACHINE_ID, ISLAND_ID, OWNER_ID, "grinder_t1", 1, new BlockKey("ci_shard_006", 2052, 81, -1016));
            machines.save(machine);
            ResourceNode node = new ResourceNode(NODE_ID, ISLAND_ID, "MINERAL", "iron_ore", 1.0D, 100, 250, 60, 1, new BlockKey("ci_shard_006", 2042, 79, -1012), 0, 0);
            handle.database().saveNode(node);
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(nodeConfig());

            SatisIslandRelocationService relocation = new SatisIslandRelocationService(machines, nodes);
            SatisIslandRelocationService.RelocationResult result = relocation.relocate(ISLAND_ID, island, "ci_shard_006", 2048, 80, -1024, false, false);

            assertEquals("0,0,0", result.delta());
            assertFalse(result.machinesRemapped());
            assertFalse(result.resourceNodesRemapped());
            assertFalse(result.machineRemapDeferred());
            assertFalse(result.resourceNodeRemapDeferred());
            assertFalse(island.hasPendingMachineRemap());
            assertFalse(island.hasPendingResourceNodeRemap());
            assertEquals("ci_shard_006", machines.find(MACHINE_ID).orElseThrow().world());
            assertEquals("ci_shard_006", nodes.nodes(ISLAND_ID).getFirst().world());
        }
    }

    @Test
    void failedEnabledNodeRemapKeepsPendingOriginAndRetriesAtSamePlacement() throws Exception {
        try (DatabaseHandle handle = openDatabase("relocation-node-retry")) {
            FactoryIsland island = new FactoryIsland(ISLAND_ID, OWNER_ID);
            island.activeWorld("ci_shard_001");
            island.activeCenterX(0);
            island.activeCenterY(64);
            island.activeCenterZ(0);
            handle.database().saveIsland(island);
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            assertTrue(machines.save(new MachineInstance(MACHINE_ID, ISLAND_ID, OWNER_ID, "grinder_t1", 1,
                    new BlockKey("ci_shard_001", 4, 65, 8))));
            handle.database().saveNode(new ResourceNode(NODE_ID, ISLAND_ID, "MINERAL", "iron_ore", 1.0D,
                    100, 250, 60, 1, new BlockKey("ci_shard_001", -6, 63, 12), 0, 0));
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(nodeConfig());
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_relocation_node BEFORE UPDATE ON resource_nodes "
                        + "WHEN NEW.world = 'ci_shard_006' BEGIN SELECT RAISE(FAIL, 'forced relocation node failure'); END");
            }
            SatisIslandRelocationService relocation = new SatisIslandRelocationService(machines, nodes);

            SatisIslandRelocationService.RelocationResult failed = relocation.relocate(
                    ISLAND_ID, island, "ci_shard_006", 2048, 80, -1024, true, true);

            assertTrue(failed.machinesRemapped());
            assertFalse(failed.resourceNodesRemapped());
            assertTrue(failed.resourceNodeRemapDeferred());
            assertTrue(island.hasPendingResourceNodeRemap());
            assertEquals("ci_shard_001", island.pendingResourceNodeRemapWorld());
            assertEquals(0, island.pendingResourceNodeRemapCenterX());
            assertEquals("ci_shard_006", machines.find(MACHINE_ID).orElseThrow().world());
            assertEquals("ci_shard_001", nodes.nodes(ISLAND_ID).getFirst().world());
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER fail_relocation_node");
            }

            SatisIslandRelocationService.RelocationResult retried = relocation.relocate(
                    ISLAND_ID, island, "ci_shard_006", 2048, 80, -1024, true, true);

            assertFalse(retried.machinesRemapped());
            assertTrue(retried.resourceNodesRemapped());
            assertFalse(retried.resourceNodeRemapDeferred());
            assertFalse(island.hasPendingResourceNodeRemap());
            assertEquals("ci_shard_006", nodes.nodes(ISLAND_ID).getFirst().world());
            assertEquals(2042, nodes.nodes(ISLAND_ID).getFirst().x());
        }
    }

    @Test
    void relocationRejectsCenterDeltaOverflowWithoutMutatingState() {
        try (DatabaseHandle handle = openDatabase("relocation-center-overflow")) {
            FactoryIsland island = new FactoryIsland(ISLAND_ID, OWNER_ID);
            island.activeWorld("ci_shard_001");
            island.activeCenterX(Integer.MIN_VALUE);
            island.activeCenterY(64);
            island.activeCenterZ(0);
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            assertTrue(machines.save(new MachineInstance(MACHINE_ID, ISLAND_ID, OWNER_ID, "grinder_t1", 1,
                    new BlockKey("ci_shard_001", 4, 65, 8))));
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(nodeConfig());
            SatisIslandRelocationService relocation = new SatisIslandRelocationService(machines, nodes);

            SatisIslandRelocationService.RelocationResult result = relocation.relocate(
                    ISLAND_ID, island, "ci_shard_006", Integer.MAX_VALUE, 80, -1024, true, true);

            assertEquals("overflow", result.delta());
            assertFalse(result.placementChanged());
            assertEquals("ci_shard_001", island.activeWorld());
            assertEquals(Integer.MIN_VALUE, island.activeCenterX());
            assertEquals("ci_shard_001", machines.find(MACHINE_ID).orElseThrow().world());
        }
    }

    @Test
    void islandMetadataSaveFailureCompensatesSuccessfulComponentRemaps() throws Exception {
        try (DatabaseHandle handle = openDatabase("relocation-metadata-compensation")) {
            FactoryIsland island = new FactoryIsland(ISLAND_ID, OWNER_ID);
            island.activeWorld("ci_shard_001");
            island.activeCenterX(0);
            island.activeCenterY(64);
            island.activeCenterZ(0);
            handle.database().saveIsland(island);
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            assertTrue(machines.save(new MachineInstance(MACHINE_ID, ISLAND_ID, OWNER_ID, "grinder_t1", 1,
                    new BlockKey("ci_shard_001", 4, 65, 8))));
            handle.database().saveNode(new ResourceNode(NODE_ID, ISLAND_ID, "MINERAL", "iron_ore", 1.0D,
                    100, 250, 60, 1, new BlockKey("ci_shard_001", -6, 63, 12), 0, 0));
            ResourceNodeService nodes = new ResourceNodeService(handle.database());
            nodes.load(nodeConfig());
            SatisIslandRelocationService relocation = new SatisIslandRelocationService(machines, nodes);
            SatisIslandRelocationService.RelocationCheckpoint checkpoint = relocation.checkpoint(island);
            SatisIslandRelocationService.RelocationResult result = relocation.relocate(
                    ISLAND_ID, island, "ci_shard_006", 2048, 80, -1024, true, true);
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_relocation_metadata BEFORE UPDATE ON factory_islands "
                        + "BEGIN SELECT RAISE(FAIL, 'forced relocation metadata failure'); END");
            }

            assertFalse(saveIsland(handle.database(), island));
            assertTrue(relocation.rollback(ISLAND_ID, island, result, checkpoint));

            assertEquals("ci_shard_001", island.activeWorld());
            assertEquals(0, island.activeCenterX());
            assertEquals("ci_shard_001", machines.find(MACHINE_ID).orElseThrow().world());
            assertEquals(4, machines.find(MACHINE_ID).orElseThrow().x());
            assertEquals("ci_shard_001", nodes.nodes(ISLAND_ID).getFirst().world());
            assertEquals(-6, nodes.nodes(ISLAND_ID).getFirst().x());
            assertEquals("ci_shard_001", handle.database().findIsland(ISLAND_ID).orElseThrow().activeWorld());
        }
    }

    private boolean saveIsland(DatabaseService database, FactoryIsland island) {
        try {
            database.saveIsland(island);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile());
        database.open();
        return new DatabaseHandle(database);
    }

    private YamlConfiguration nodeConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("resource-nodes.regeneration.enabled", false);
        config.set("resource-nodes.regeneration.minimum-interval-ms", 0);
        return config;
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }
}
