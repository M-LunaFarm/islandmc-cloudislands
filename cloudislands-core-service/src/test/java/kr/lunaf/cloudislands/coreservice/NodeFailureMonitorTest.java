package kr.lunaf.cloudislands.coreservice;

import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.InMemoryIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeFailureMonitorTest {
    private static final UUID ISLAND_A = UUID.fromString("00000000-0000-0000-0000-000000001201");
    private static final UUID ISLAND_B = UUID.fromString("00000000-0000-0000-0000-000000001202");
    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-000000001301");
    private static final UUID OWNER_B = UUID.fromString("00000000-0000-0000-0000-000000001302");

    @Test
    void marksActiveIslandOnFailedNodeAsRecoveryRequired() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(ISLAND_A, OWNER_A, "default", "alpha");
        islands.createOwnedIsland(ISLAND_B, OWNER_B, "default", "bravo");
        runtimes.markActive(ISLAND_A, "failed-node", "ci_shard_001", 2, 3, 9L);
        runtimes.setState(ISLAND_B, IslandState.INACTIVE_READY);

        NodeFailureMonitor monitor = new NodeFailureMonitor(new InMemoryNodeRegistry(0), runtimes, islands, events, Duration.ofSeconds(5));
        int affected = monitor.markRecoveryRequiredForNode("failed-node");

        assertEquals(1, affected);
        assertEquals(IslandState.RECOVERY_REQUIRED, runtimes.find(ISLAND_A).orElseThrow().state());
        assertEquals("failed-node", runtimes.find(ISLAND_A).orElseThrow().activeNode());
        assertEquals(IslandState.RECOVERY_REQUIRED, islands.findById(ISLAND_A).map(IslandSnapshot::state).orElseThrow());
        assertEquals(IslandState.CREATING, islands.findById(ISLAND_B).map(IslandSnapshot::state).orElseThrow());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name()));
        assertTrue(events.countsByField(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), "reason").containsKey("NODE_DOWN"));
    }

    @Test
    void queuesRecoveryRestoreFromLatestSnapshotOnAnotherReadyNode() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        islands.createOwnedIsland(ISLAND_A, OWNER_A, "default", "alpha");
        runtimes.markActive(ISLAND_A, "failed-node", "ci_shard_001", 2, 3, 9L);
        snapshots.record(ISLAND_A, 42L, "islands/" + ISLAND_A + "/snapshots/000042/bundle.tar.zst", "PERIODIC", OWNER_A, "abc123", 2048L);
        IslandLifecycleWorkflow lifecycle = lifecycle(runtimes, islands, jobs, events);
        NodeFailureMonitor monitor = new NodeFailureMonitor(new InMemoryNodeRegistry(3), runtimes, islands, events, Duration.ofSeconds(5), null, null, snapshots, lifecycle);

        monitor.markRecoveryRequiredForNode("failed-node");
        int queued = monitor.recoverOrQuarantineNodeIslands("failed-node");

        assertEquals(1, queued);
        assertEquals(IslandState.RESTORING, runtimes.find(ISLAND_A).orElseThrow().state());
        assertEquals(IslandState.RESTORING, islands.findById(ISLAND_A).map(IslandSnapshot::state).orElseThrow());
        List<IslandJob> pendingJobs = jobs.snapshot();
        assertEquals(1, pendingJobs.size());
        IslandJob restore = pendingJobs.get(0);
        assertEquals(IslandJobType.RESTORE_ISLAND, restore.type());
        assertEquals(ISLAND_A, restore.islandId());
        assertFalse("failed-node".equals(restore.targetNode()));
        assertEquals("42", restore.payload().get("snapshotNo"));
        assertEquals("true", restore.payload().get("recoveryRestore"));
        assertEquals("recovery-restore", restore.payload().get("placementSource"));
    }

    @Test
    void quarantinesRecoveryIslandWhenNoSnapshotExists() {
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        islands.createOwnedIsland(ISLAND_A, OWNER_A, "default", "alpha");
        runtimes.markActive(ISLAND_A, "failed-node", "ci_shard_001", 2, 3, 9L);
        IslandLifecycleWorkflow lifecycle = lifecycle(runtimes, islands, jobs, events);
        NodeFailureMonitor monitor = new NodeFailureMonitor(new InMemoryNodeRegistry(3), runtimes, islands, events, Duration.ofSeconds(5), null, null, snapshots, lifecycle);

        monitor.markRecoveryRequiredForNode("failed-node");
        int queued = monitor.recoverOrQuarantineNodeIslands("failed-node");

        assertEquals(0, queued);
        assertEquals(IslandState.QUARANTINED, runtimes.find(ISLAND_A).orElseThrow().state());
        assertEquals(IslandState.QUARANTINED, islands.findById(ISLAND_A).map(IslandSnapshot::state).orElseThrow());
        assertTrue(jobs.snapshot().isEmpty());
        assertTrue(events.countsByField(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), "reason").containsKey("RECOVERY_QUARANTINED_MISSING_SNAPSHOT"));
    }

    private static IslandLifecycleWorkflow lifecycle(
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
