package kr.lunaf.cloudislands.coreservice.job;

import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.RedisActivationLock;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.InMemoryIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobCompletionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Test
    void staleSaveCompletionDoesNotRecordSnapshot() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        JobCompletionService service = service(runtimes, events, snapshots);
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 5, 3, 2L);

        service.completed(job(IslandJobType.SAVE_ISLAND, "island-1", Map.of(
            "fencingToken", "1",
            "snapshotNo", "10",
            "storagePath", "islands/" + ISLAND + "/snapshots/000010/bundle.tar.zst",
            "checksum", "old",
            "sizeBytes", "1024"
        )));

        assertTrue(snapshots.list(ISLAND, 10).isEmpty());
        assertEquals(IslandState.ACTIVE, runtimes.find(ISLAND).orElseThrow().state());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name()));
        assertTrue(events.toJson().contains("STALE_FENCING_TOKEN"));
    }

    @Test
    void sameTokenSaveCompletionFromStaleNodeDoesNotRecordSnapshot() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        JobCompletionService service = service(runtimes, events, snapshots);
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 5, 3, 8L);

        service.completed(job(IslandJobType.SAVE_ISLAND, "island-1", Map.of(
            "fencingToken", "8",
            "snapshotNo", "14",
            "storagePath", "islands/" + ISLAND + "/snapshots/000014/bundle.tar.zst",
            "checksum", "stale-node",
            "sizeBytes", "1024"
        )));

        assertTrue(snapshots.list(ISLAND, 10).isEmpty());
        assertEquals(IslandState.ACTIVE, runtimes.find(ISLAND).orElseThrow().state());
        assertEquals("island-2", runtimes.find(ISLAND).orElseThrow().activeNode());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name()));
        assertTrue(events.toJson().contains("STALE_NODE_COMPLETION"));
    }

    @Test
    void staleDeleteCompletionDoesNotMoveRuntimeTowardDelete() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        JobCompletionService service = service(runtimes, events, snapshots);
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 5, 3, 4L);

        service.completed(job(IslandJobType.DELETE_ISLAND, "island-1", Map.of(
            "fencingToken", "3",
            "snapshotNo", "11",
            "reason", "DELETE_ISLAND",
            "checksum", "old-delete",
            "sizeBytes", "2048"
        )));

        assertTrue(snapshots.list(ISLAND, 10).isEmpty());
        assertEquals(IslandState.ACTIVE, runtimes.find(ISLAND).orElseThrow().state());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name()));
        assertTrue(events.toJson().contains("STALE_FENCING_TOKEN"));
    }

    @Test
    void fencedCompletionWithoutRuntimeIsIgnored() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        JobCompletionService service = service(runtimes, events, snapshots);

        service.completed(job(IslandJobType.SAVE_ISLAND, "island-1", Map.of(
            "fencingToken", "5",
            "snapshotNo", "12",
            "storagePath", "islands/" + ISLAND + "/snapshots/000012/bundle.tar.zst",
            "checksum", "missing-runtime",
            "sizeBytes", "4096"
        )));

        assertTrue(runtimes.find(ISLAND).isEmpty());
        assertTrue(snapshots.list(ISLAND, 10).isEmpty());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name()));
        assertTrue(events.toJson().contains("RUNTIME_MISSING"));
    }

    @Test
    void snapshotCompletionKeepsCommittedSnapshotWhenEventPublishFails() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        JobCompletionService service = service(runtimes, (_eventType, _fields) -> {
            throw new IllegalStateException("event sink down");
        }, snapshots);
        runtimes.markActive(ISLAND, "island-1", "ci_shard_001", 1, 2, 6L);

        assertDoesNotThrow(() -> service.completed(job(IslandJobType.SAVE_ISLAND, "island-1", Map.of(
            "fencingToken", "6",
            "snapshotNo", "13",
            "storagePath", "islands/" + ISLAND + "/snapshots/000013/bundle.tar.zst",
            "checksum", "saved-before-event-failure",
            "sizeBytes", "4096"
        ))));

        assertEquals("saved-before-event-failure", snapshots.find(ISLAND, 13L).orElseThrow().checksum());
        assertEquals(IslandState.ACTIVE, runtimes.find(ISLAND).orElseThrow().state());
    }

    @Test
    void duplicateCompletionPayloadReplaysAndDifferentPayloadConflicts() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        JobCompletionService service = service(runtimes, events, snapshots);
        runtimes.markActive(ISLAND, "island-1", "ci_shard_001", 1, 2, 9L);
        IslandJob claimed = job(IslandJobType.SAVE_ISLAND, "island-1", Map.of("fencingToken", "9"));
        Map<String, String> payload = Map.of(
            "snapshotNo", "21",
            "reason", "MANUAL",
            "checksum", "first",
            "sizeBytes", "512"
        );

        JobCompletionResult first = service.completed(claimed, payload);
        JobCompletionResult replay = service.completed(claimed, payload);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.requestHash(), replay.requestHash());
        assertEquals(1, snapshots.list(ISLAND, 10).size());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name()));
        assertEquals(1, events.countsByField(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), "eventId").size());
        assertEquals(Map.of("1", 1L), events.countsByField(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), "aggregateVersion"));
        assertThrows(JobCompletionConflictException.class, () -> service.completed(claimed, Map.of(
            "snapshotNo", "22",
            "reason", "MANUAL",
            "checksum", "different",
            "sizeBytes", "512"
        )));
    }

    @Test
    void restoreCompletionRecordsPreRestoreSnapshotAndReleasesRestoreLock() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        RedisActivationLock activationLock = new RedisActivationLock(URI.create("redis://127.0.0.1:1"), Duration.ofSeconds(30), true);
        JobCompletionService service = service(runtimes, islands, events, snapshots, activationLock);
        islands.createOwnedIsland(ISLAND, OWNER, "default", "restore target");
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 7, 8, 42L);
        runtimes.setState(ISLAND, IslandState.RESTORING);
        RedisActivationLock.Lease lease = activationLock.acquire(ISLAND, "restore").orElseThrow();

        service.completed(job(IslandJobType.RESTORE_ISLAND, "island-2", Map.ofEntries(
            Map.entry("fencingToken", "42"),
            Map.entry("activationLockToken", lease.token()),
            Map.entry("snapshotNo", "9"),
            Map.entry("worldName", "ci_shard_002"),
            Map.entry("cellX", "7"),
            Map.entry("cellZ", "8"),
            Map.entry("preMutationSnapshotNo", "10"),
            Map.entry("preMutationReason", "BEFORE_RESTORE"),
            Map.entry("preMutationChecksum", "pre-restore-checksum"),
            Map.entry("preMutationSizeBytes", "8192"),
            Map.entry("resetRuntimeBeforeReactivate", "true"),
            Map.entry("reactivateAfterRestore", "true")
        )));

        assertEquals(IslandState.ACTIVE, runtimes.find(ISLAND).orElseThrow().state());
        assertEquals(IslandState.ACTIVE, islands.findById(ISLAND).orElseThrow().state());
        assertEquals(1, snapshots.list(ISLAND, 10).size());
        assertEquals("BEFORE_RESTORE", snapshots.find(ISLAND, 10L).orElseThrow().reason());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RESTORED.name()));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name()));
        assertTrue(activationLock.acquire(ISLAND, "restore").isPresent());
    }

    @Test
    void staleRestoreCompletionCannotReleaseCurrentLockWithOwnerPrefixOnly() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        RedisActivationLock activationLock = new RedisActivationLock(URI.create("redis://127.0.0.1:1"), Duration.ofSeconds(30), true);
        JobCompletionService service = service(runtimes, islands, events, snapshots, activationLock);
        islands.createOwnedIsland(ISLAND, OWNER, "default", "restore target");
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 7, 8, 43L);
        runtimes.setState(ISLAND, IslandState.RESTORING);
        RedisActivationLock.Lease current = activationLock.acquire(ISLAND, "restore").orElseThrow();

        service.completed(job(IslandJobType.RESTORE_ISLAND, "island-2", Map.ofEntries(
            Map.entry("fencingToken", "43"),
            Map.entry("activationLockToken", "restore:expired-old-token"),
            Map.entry("snapshotNo", "9"),
            Map.entry("worldName", "ci_shard_002"),
            Map.entry("cellX", "7"),
            Map.entry("cellZ", "8"),
            Map.entry("resetRuntimeBeforeReactivate", "true"),
            Map.entry("reactivateAfterRestore", "true")
        )));

        assertFalse(activationLock.acquire(ISLAND, "restore").isPresent());
        activationLock.release(current);
        assertTrue(activationLock.acquire(ISLAND, "restore").isPresent());
    }

    private JobCompletionService service(InMemoryIslandRuntimeRepository runtimes, GlobalEventPublisher events, InMemoryIslandSnapshotRepository snapshots) {
        return new JobCompletionService(
            runtimes,
            events,
            snapshots,
            new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC))
        );
    }

    private JobCompletionService service(
        InMemoryIslandRuntimeRepository runtimes,
        InMemoryIslandRepository islands,
        InMemoryGlobalEventPublisher events,
        InMemoryIslandSnapshotRepository snapshots,
        RedisActivationLock activationLock
    ) {
        return new JobCompletionService(
            runtimes,
            events,
            snapshots,
            new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC)),
            null,
            islands,
            null,
            Duration.ofSeconds(30),
            SnapshotRetentionPolicy.defaultPolicy(),
            activationLock
        );
    }

    private IslandJob job(IslandJobType type, String targetNode, Map<String, String> payload) {
        return new IslandJob(UUID.randomUUID(), type, ISLAND, targetNode, 0, payload, NOW);
    }
}
