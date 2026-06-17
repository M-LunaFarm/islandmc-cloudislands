package kr.lunaf.cloudislands.coreservice.workflow;

import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.storage.BundleRestorePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandLifecycleWorkflowRestoreTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000702");

    @Test
    void activeRestoreQueuesPreRestoreSnapshotAndLobbyTransferPolicy() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(ISLAND, OWNER, "default", "restore target");
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 7, 8, 42L);

        IslandLifecycleWorkflow.Result result = workflow(runtimes, islands, jobs, events).restore(ISLAND, 9L, "islands/" + ISLAND + "/snapshots/000009/bundle.tar.zst");

        assertEquals(true, result.accepted());
        assertEquals("RESTORE_QUEUED", result.code());
        assertEquals(IslandState.RESTORING, runtimes.find(ISLAND).orElseThrow().state());
        assertEquals(IslandState.RESTORING, islands.findById(ISLAND).map(IslandSnapshot::state).orElseThrow());
        List<IslandJob> queued = jobs.snapshot();
        assertEquals(1, queued.size());
        IslandJob restore = queued.get(0);
        assertEquals(IslandJobType.RESTORE_ISLAND, restore.type());
        assertEquals("island-2", restore.targetNode());
        assertEquals("9", restore.payload().get("snapshotNo"));
        assertEquals("true", restore.payload().get("preRestoreSnapshotRequired"));
        assertEquals("BEFORE_RESTORE", restore.payload().get("preRestoreReason"));
        assertEquals("true", restore.payload().get("transferActivePlayersToLobby"));
        assertEquals("lobby", restore.payload().get("playerTransferTarget"));
        assertEquals("true", restore.payload().get("resetRuntimeBeforeReactivate"));
        assertEquals("true", restore.payload().get("reactivateAfterRestore"));
        assertEquals(BundleRestorePolicy.ROLLBACK_POLICY, restore.payload().get("rollbackPolicy"));
        assertEquals("ci_shard_002", restore.payload().get("worldName"));
        assertEquals("7", restore.payload().get("cellX"));
        assertEquals("8", restore.payload().get("cellZ"));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RESTORE_REQUESTED.name()));
        String eventJson = events.toJson();
        assertEquals(true, eventJson.contains("\"preRestoreSnapshotRequired\":\"true\""));
        assertEquals(true, eventJson.contains("\"transferActivePlayersToLobby\":\"true\""));
        assertEquals(true, eventJson.contains("\"rollbackPolicy\":\"" + BundleRestorePolicy.ROLLBACK_POLICY + "\""));
    }

    private IslandLifecycleWorkflow workflow(
        InMemoryIslandRuntimeRepository runtimes,
        InMemoryIslandRepository islands,
        InMemoryIslandJobPublisher jobs,
        InMemoryGlobalEventPublisher events
    ) {
        return new IslandLifecycleWorkflow(
            runtimes,
            islands,
            new InMemoryIslandTemplateRepository(),
            new InMemoryNodeRegistry(3),
            new NodeAllocator(Duration.ofSeconds(5)),
            jobs,
            events
        );
    }
}
