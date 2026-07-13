package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void failedMachineInsertDoesNotCreateRuntimeGhost() throws Exception {
        try (DatabaseHandle handle = openDatabase("machine-save-failure")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            UUID machineId = UUID.fromString("00000000-0000-0000-0000-000000004701");
            MachineInstance machine = new MachineInstance(machineId,
                    UUID.fromString("00000000-0000-0000-0000-000000004700"),
                    UUID.fromString("00000000-0000-0000-0000-000000004799"),
                    "grinder_t1", 1, new BlockKey("world", 7, 64, 0));
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_machine_save BEFORE INSERT ON machines BEGIN SELECT RAISE(FAIL, 'forced machine failure'); END");
            }

            assertFalse(machines.save(machine));

            assertTrue(machines.find(machineId).isEmpty());
            assertTrue(machines.all().isEmpty());
            assertTrue(handle.database().loadMachines().isEmpty());
        }
    }

    @Test
    void failedInventoryInsertDoesNotCreateRuntimeGhost() throws Exception {
        try (DatabaseHandle handle = openDatabase("inventory-save-failure")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            UUID inventoryId = UUID.fromString("00000000-0000-0000-0000-000000004801");
            VirtualInventory inventory = new VirtualInventory(inventoryId,
                    UUID.fromString("00000000-0000-0000-0000-000000004800"),
                    "MACHINE_INPUT", "00000000-0000-0000-0000-000000004899", 64);
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_inventory_save BEFORE INSERT ON virtual_inventories BEGIN SELECT RAISE(FAIL, 'forced inventory failure'); END");
            }

            assertFalse(storage.saveNow(inventory));

            assertTrue(storage.get(inventoryId).isEmpty());
            assertTrue(handle.database().loadInventory(inventoryId).isEmpty());
        }
    }

    @Test
    void rejectedDirtySaveDoesNotCreateRuntimeGhosts() {
        try (DatabaseHandle handle = openDatabase("dirty-save-authority-rejected")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            DirtySaveService dirtySaves = new DirtySaveService(null, handle.database());
            dirtySaves.islandRuntimeAuthority(_islandUuid -> false);
            storage.dirtySaves(dirtySaves);
            machines.dirtySaves(dirtySaves);
            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000004900");
            UUID machineId = UUID.fromString("00000000-0000-0000-0000-000000004901");
            UUID inventoryId = UUID.fromString("00000000-0000-0000-0000-000000004902");
            MachineInstance machine = new MachineInstance(machineId, islandUuid,
                    UUID.fromString("00000000-0000-0000-0000-000000004999"),
                    "grinder_t1", 1, new BlockKey("world", 9, 64, 0));
            VirtualInventory inventory = new VirtualInventory(inventoryId, islandUuid,
                    "MACHINE_INPUT", machineId.toString(), 64);

            assertFalse(machines.saveLater(machine));
            assertFalse(storage.saveIfAllowed(inventory));

            assertTrue(machines.find(machineId).isEmpty());
            assertTrue(storage.get(inventoryId).isEmpty());
            assertEquals(0, dirtySaves.pendingWrites());
        }
    }

    @Test
    void failedMachineBundleDeleteRollsBackBuffersAndKeepsRuntimeState() throws Exception {
        try (DatabaseHandle handle = openDatabase("machine-delete-failure")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004911");
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_machine_delete BEFORE DELETE ON machines BEGIN SELECT RAISE(FAIL, 'forced machine delete failure'); END");
            }

            assertFalse(machines.remove(bundle.machine()));

            assertTrue(machines.find(bundle.machine().machineId()).isPresent());
            assertTrue(storage.get(bundle.input().inventoryId()).isPresent());
            assertTrue(storage.get(bundle.output().inventoryId()).isPresent());
            assertTrue(handle.database().loadInventory(bundle.input().inventoryId()).isPresent());
            assertTrue(handle.database().loadInventory(bundle.output().inventoryId()).isPresent());
            assertTrue(handle.database().loadMachines().stream()
                    .anyMatch(machine -> machine.machineId().equals(bundle.machine().machineId())));
        }
    }

    @Test
    void failedStandaloneInventoryDeleteKeepsRuntimeAndDurableState() throws Exception {
        try (DatabaseHandle handle = openDatabase("inventory-delete-failure")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000004920");
            UUID inventoryId = UUID.fromString("00000000-0000-0000-0000-000000004921");
            VirtualInventory inventory = new VirtualInventory(inventoryId, islandUuid, "MACHINE_INPUT",
                    "00000000-0000-0000-0000-000000004922", 64);
            assertTrue(storage.saveNow(inventory));
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_inventory_delete BEFORE DELETE ON virtual_inventories BEGIN SELECT RAISE(FAIL, 'forced inventory delete failure'); END");
            }

            assertFalse(storage.delete(inventoryId));

            assertTrue(storage.get(inventoryId).isPresent());
            assertTrue(handle.database().loadInventory(inventoryId).isPresent());
        }
    }

    @Test
    void normalRemoveRejectsMachineWithBufferedItems() {
        try (DatabaseHandle handle = openDatabase("normal-remove")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004001");
            bundle.input().add("wheat", 10);
            storage.saveNow(bundle.input());

            assertFalse(machines.remove(bundle.machine()));

            assertTrue(machines.find(bundle.machine().machineId()).isPresent());
            assertEquals(10, storage.get(bundle.input().inventoryId()).orElseThrow().amount("wheat"));
            assertTrue(handle.database().loadMachines().stream()
                    .anyMatch(machine -> machine.machineId().equals(bundle.machine().machineId())));
        }
    }

    @Test
    void forceRemoveFlushesBufferedItemsAndDeletesMachine() {
        try (DatabaseHandle handle = openDatabase("force-remove")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004101");
            bundle.input().add("wheat", 10);
            storage.saveNow(bundle.input());

            machines.forceRemove(bundle.machine());

            assertTrue(machines.find(bundle.machine().machineId()).isEmpty());
            assertTrue(handle.database().loadMachines().stream()
                    .noneMatch(machine -> machine.machineId().equals(bundle.machine().machineId())));
            assertEquals(10, storage.islandStorage(bundle.machine().islandUuid()).amount("wheat"));
            assertTrue(storage.get(bundle.input().inventoryId()).isEmpty());
            assertTrue(storage.get(bundle.output().inventoryId()).isEmpty());
            assertTrue(handle.database().loadInventory(bundle.input().inventoryId()).isEmpty());
            assertTrue(handle.database().loadInventory(bundle.output().inventoryId()).isEmpty());
        }
    }

    @Test
    void forceRemovePersistsFlushedItemsBeforeDeletingDirtyBuffers() {
        try (DatabaseHandle handle = openDatabase("force-remove-dirty")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            DirtySaveService dirtySaves = new DirtySaveService(null, handle.database());
            storage.dirtySaves(dirtySaves);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            machines.dirtySaves(dirtySaves);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004501");
            bundle.input().add("iron_ore", 12);
            storage.saveNow(bundle.input());

            machines.forceRemove(bundle.machine());

            UUID islandStorageId = storage.islandStorage(bundle.machine().islandUuid()).inventoryId();
            assertEquals(12, handle.database().loadInventory(islandStorageId).orElseThrow().amount("iron_ore"));
            assertTrue(handle.database().loadInventory(bundle.input().inventoryId()).isEmpty());
            assertTrue(handle.database().loadInventory(bundle.output().inventoryId()).isEmpty());
        }
    }

    @Test
    void reactivateWakesRecoverableMachinesAndBumpsRevision() {
        try (DatabaseHandle handle = openDatabase("reactivate")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004201");
            long before = machines.revision();
            bundle.machine().status(MachineStatus.NO_INPUT);

            machines.reactivate(bundle.machine());

            assertEquals(MachineStatus.SLEEPING, bundle.machine().status());
            assertTrue(machines.revision() > before);
        }
    }

    @Test
    void reactivateDoesNotWakeTerminalMachineStates() {
        try (DatabaseHandle handle = openDatabase("reactivate-terminal")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004301");
            long before = machines.revision();
            bundle.machine().status(MachineStatus.BROKEN);

            machines.reactivate(bundle.machine());

            assertEquals(MachineStatus.BROKEN, bundle.machine().status());
            assertEquals(before, machines.revision());
        }
    }

    @Test
    void reactivatePowerBlockedWakesNoPowerMachinesOnIsland() {
        try (DatabaseHandle handle = openDatabase("reactivate-power")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004401");
            long before = machines.revision();
            bundle.machine().status(MachineStatus.NO_POWER);

            machines.reactivatePowerBlocked(bundle.machine().islandUuid());

            assertEquals(MachineStatus.SLEEPING, bundle.machine().status());
            assertTrue(machines.revision() > before);
        }
    }

    @Test
    void remapIslandRegionMovesMachinesByCenterDelta() {
        try (DatabaseHandle handle = openDatabase("remap-region")) {
            StorageService storage = new StorageService(handle.database(), 1000);
            MachineService machines = new MachineService(handle.database(), new MachineDefinitionService(), storage);
            MachineBundle bundle = machineWithInput(storage, machines, "00000000-0000-0000-0000-000000004601");

            assertTrue(machines.remapIslandRegion(bundle.machine().islandUuid(), "ci_shard_002", 1024, 16, -2048));

            MachineInstance remapped = machines.find(bundle.machine().machineId()).orElseThrow();
            assertEquals("ci_shard_002", remapped.world());
            assertEquals(1024, remapped.x());
            assertEquals(80, remapped.y());
            assertEquals(-2048, remapped.z());
            MachineInstance persisted = handle.database().loadMachines().stream()
                    .filter(machine -> machine.machineId().equals(bundle.machine().machineId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("ci_shard_002", persisted.world());
            assertEquals(1024, persisted.x());
            assertEquals(80, persisted.y());
            assertEquals(-2048, persisted.z());
        }
    }

    private MachineBundle machineWithInput(StorageService storage, MachineService machines, String machineUuid) {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000004900");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000004901");
        MachineInstance machine = new MachineInstance(UUID.fromString(machineUuid), islandUuid, ownerUuid,
                "grinder_t1", 1, new BlockKey("world", machineUuid.endsWith("4101") ? 1 : 0, 64, 0));
        VirtualInventory input = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_INPUT", 64);
        VirtualInventory output = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_OUTPUT", 64);
        machine.inputInventoryId(input.inventoryId());
        machine.outputInventoryId(output.inventoryId());
        machines.save(machine);
        return new MachineBundle(machine, input, output);
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile());
        database.open();
        return new DatabaseHandle(database);
    }

    private record MachineBundle(MachineInstance machine, VirtualInventory input, VirtualInventory output) {
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }
}
