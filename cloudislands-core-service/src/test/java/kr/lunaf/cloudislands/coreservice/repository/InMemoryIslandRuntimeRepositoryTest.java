package kr.lunaf.cloudislands.coreservice.repository;

import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryIslandRuntimeRepositoryTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000901");

    @Test
    void staleActiveCompletionCannotMoveIslandToOlderRuntimeOwner() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 7, 8, 42L);

        IslandRuntimeSnapshot stale = runtimes.markActive(ISLAND, "island-2", "ci_shard_001", 1, 1, 41L);

        assertEquals(IslandState.ACTIVE, stale.state());
        assertEquals("island-2", stale.activeNode());
        assertEquals("ci_shard_002", stale.activeWorld());
        assertEquals(42L, stale.fencingToken());
    }

    @Test
    void staleInactiveCompletionCannotUnloadNewerActiveRuntime() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.markActive(ISLAND, "island-3", "ci_shard_003", 9, 4, 12L);

        IslandRuntimeSnapshot stale = runtimes.markInactive(ISLAND, 11L);

        assertEquals(IslandState.ACTIVE, stale.state());
        assertEquals("island-3", stale.activeNode());
        assertEquals(12L, stale.fencingToken());
    }

    @Test
    void staleSaveAttemptCannotMoveNewerRuntimeIntoSaving() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 5, 3, 102L);

        IslandRuntimeSnapshot stale = runtimes.markSaving(ISLAND, 101L);

        assertEquals(IslandState.ACTIVE, stale.state());
        assertEquals("island-2", stale.activeNode());
        assertEquals("ci_shard_002", stale.activeWorld());
        assertEquals(102L, stale.fencingToken());
    }

    @Test
    void currentSaveAttemptMovesRuntimeIntoSavingWithSameToken() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 5, 3, 102L);

        IslandRuntimeSnapshot saving = runtimes.markSaving(ISLAND, 102L);

        assertEquals(IslandState.SAVING, saving.state());
        assertEquals("island-2", saving.activeNode());
        assertEquals(102L, saving.fencingToken());
    }

    @Test
    void runningIslandCannotBeActivatedOnDifferentNode() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.markActive(ISLAND, "island-4", "ci_shard_004", 3, 5, 20L);

        assertThrows(IllegalStateException.class,
                () -> runtimes.markActive(ISLAND, "island-5", "ci_shard_005", 6, 7, 21L));

        IslandRuntimeSnapshot current = runtimes.find(ISLAND).orElseThrow();
        assertEquals(IslandState.ACTIVE, current.state());
        assertEquals("island-4", current.activeNode());
        assertEquals(20L, current.fencingToken());
    }

    @Test
    void runningIslandCannotStartDuplicateActivationOnSameNode() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.markActivating(ISLAND, "island-4", "ci_shard_004", 3, 5);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> runtimes.markActivating(ISLAND, "island-4", "ci_shard_004", 3, 5));

        assertEquals("ACTIVATION_LOCKED", exception.getMessage());
        IslandRuntimeSnapshot current = runtimes.find(ISLAND).orElseThrow();
        assertEquals(IslandState.ACTIVATING, current.state());
        assertEquals("island-4", current.activeNode());
        assertEquals(1L, current.fencingToken());
    }

    @Test
    void genericStateSetterRejectsLifecycleBypass() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();

        assertThrows(IllegalStateException.class,
                () -> runtimes.setState(ISLAND, IslandState.ACTIVE));

        assertTrue(runtimes.find(ISLAND).isEmpty());
    }

    @Test
    void genericStateSetterAcceptsPinnedRestoreAndDeleteFlows() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();

        runtimes.setState(ISLAND, IslandState.RESTORING);
        runtimes.setState(ISLAND, IslandState.ACTIVATING);
        runtimes.setState(ISLAND, IslandState.ACTIVE);
        runtimes.setState(ISLAND, IslandState.DELETE_REQUESTED);
        runtimes.setState(ISLAND, IslandState.DEACTIVATING);
        runtimes.setState(ISLAND, IslandState.BACKUP_BEFORE_DELETE);
        runtimes.setState(ISLAND, IslandState.DELETING);
        IslandRuntimeSnapshot deleted = runtimes.setState(ISLAND, IslandState.DELETED);

        assertEquals(IslandState.DELETED, deleted.state());
    }

    @Test
    void terminalDeletedRuntimeCannotMoveBackIntoRecovery() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.setState(ISLAND, IslandState.DELETE_REQUESTED);
        runtimes.setState(ISLAND, IslandState.DEACTIVATING);
        runtimes.setState(ISLAND, IslandState.BACKUP_BEFORE_DELETE);
        runtimes.setState(ISLAND, IslandState.DELETING);
        runtimes.setState(ISLAND, IslandState.DELETED);

        assertThrows(IllegalStateException.class,
                () -> runtimes.setState(ISLAND, IslandState.RECOVERY_REQUIRED));
        assertEquals(IslandState.DELETED, runtimes.find(ISLAND).orElseThrow().state());
    }
}
