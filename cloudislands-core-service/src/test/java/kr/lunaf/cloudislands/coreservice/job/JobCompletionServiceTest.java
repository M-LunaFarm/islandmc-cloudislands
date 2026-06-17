package kr.lunaf.cloudislands.coreservice.job;

import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.InMemoryIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobCompletionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000301");

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

    private JobCompletionService service(InMemoryIslandRuntimeRepository runtimes, InMemoryGlobalEventPublisher events, InMemoryIslandSnapshotRepository snapshots) {
        return new JobCompletionService(
            runtimes,
            events,
            snapshots,
            new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC))
        );
    }

    private IslandJob job(IslandJobType type, String targetNode, Map<String, String> payload) {
        return new IslandJob(UUID.randomUUID(), type, ISLAND, targetNode, 0, payload, NOW);
    }
}
