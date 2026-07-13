package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.hook.IslandRef;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryIslandServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void failedIslandInsertDoesNotCreateRuntimeGhost() throws Exception {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000005101");
        try (DatabaseHandle handle = openDatabase("island-save-failure")) {
            FactoryIslandService islands = new FactoryIslandService(null, handle.database());
            FactoryIsland island = new FactoryIsland(islandUuid,
                    UUID.fromString("00000000-0000-0000-0000-000000005102"));
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_island_save BEFORE INSERT ON factory_islands BEGIN SELECT RAISE(FAIL, 'forced island failure'); END");
            }

            assertFalse(islands.save(island));

            assertTrue(islands.find(islandUuid).isEmpty());
            assertTrue(islands.cached().isEmpty());
            assertTrue(handle.database().findIsland(islandUuid).isEmpty());
        }
    }

    @Test
    void rejectedDirtySaveDoesNotCreateIslandRuntimeGhost() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000005111");
        try (DatabaseHandle handle = openDatabase("island-authority-rejected")) {
            FactoryIslandService islands = new FactoryIslandService(null, handle.database());
            DirtySaveService dirtySaves = new DirtySaveService(null, handle.database());
            dirtySaves.islandRuntimeAuthority(_islandUuid -> false);
            islands.dirtySaves(dirtySaves);

            assertFalse(islands.save(new FactoryIsland(islandUuid,
                    UUID.fromString("00000000-0000-0000-0000-000000005112"))));

            assertTrue(islands.find(islandUuid).isEmpty());
            assertEquals(0, dirtySaves.pendingWrites());
        }
    }

    @Test
    void ownerSynchronizationFailureRestoresCachedOwner() throws Exception {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000005121");
        UUID originalOwner = UUID.fromString("00000000-0000-0000-0000-000000005122");
        UUID transferredOwner = UUID.fromString("00000000-0000-0000-0000-000000005123");
        try (DatabaseHandle handle = openDatabase("owner-sync-failure")) {
            FactoryIslandService islands = new FactoryIslandService(null, handle.database());
            FactoryIsland island = islands.getOrCreate(islandRef(islandUuid, originalOwner));
            try (Connection connection = handle.database().connection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_owner_sync BEFORE UPDATE ON factory_islands BEGIN SELECT RAISE(FAIL, 'forced owner sync failure'); END");
            }

            FactoryIsland unchanged = islands.getOrCreate(islandRef(islandUuid, transferredOwner));

            assertSame(island, unchanged);
            assertEquals(originalOwner, unchanged.ownerUuid());
            assertEquals(originalOwner, handle.database().findIsland(islandUuid).orElseThrow().ownerUuid());
        }
    }

    @Test
    void getOrCreateSynchronizesCachedOwnerWithoutLosingIslandState() {
        UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000005001");
        UUID originalOwner = UUID.fromString("00000000-0000-0000-0000-000000005002");
        UUID transferredOwner = UUID.fromString("00000000-0000-0000-0000-000000005003");

        try (DatabaseHandle handle = openDatabase("owner-sync")) {
            FactoryIslandService islands = new FactoryIslandService(null, handle.database());
            FactoryIsland island = islands.getOrCreate(islandRef(islandUuid, originalOwner));
            island.researchPoints(75);
            islands.save(island);

            FactoryIsland transferred = islands.getOrCreate(islandRef(islandUuid, transferredOwner));

            assertSame(island, transferred);
            assertEquals(transferredOwner, transferred.ownerUuid());
            assertEquals(75, transferred.researchPoints());
            FactoryIsland persisted = handle.database().findIsland(islandUuid).orElseThrow();
            assertEquals(transferredOwner, persisted.ownerUuid());
            assertEquals(75, persisted.researchPoints());
        }
    }

    private IslandRef islandRef(UUID islandUuid, UUID ownerUuid) {
        return new IslandRef(null, islandUuid, ownerUuid);
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile(), "data.db");
        database.open();
        return new DatabaseHandle(database);
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }
}
