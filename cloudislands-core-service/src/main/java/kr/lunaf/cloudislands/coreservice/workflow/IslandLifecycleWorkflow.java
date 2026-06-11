package kr.lunaf.cloudislands.coreservice.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class IslandLifecycleWorkflow {
    private final IslandRuntimeRepository runtimes;
    private final NodeRegistry nodes;
    private final NodeAllocator allocator;
    private final IslandJobPublisher jobs;
    private final GlobalEventPublisher events;

    public IslandLifecycleWorkflow(IslandRuntimeRepository runtimes, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events) {
        this.runtimes = runtimes;
        this.nodes = nodes;
        this.allocator = allocator;
        this.jobs = jobs;
        this.events = events;
    }

    public Result activate(UUID islandId) {
        NodeLoad node = allocator.selectBestNode(nodes.snapshot(), Instant.now()).orElse(null);
        if (node == null) {
            return new Result(false, "NODE_UNAVAILABLE", null);
        }
        IslandRuntimeSnapshot runtime = runtimes.markActivating(islandId, node.nodeId(), "ci_shard_001", 0, 0);
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.ACTIVATE_ISLAND, islandId, node.nodeId(), 0, Map.of("fencingToken", Long.toString(runtime.fencingToken()), "worldName", runtime.activeWorld() == null ? "ci_shard_001" : runtime.activeWorld(), "cellX", runtime.cellX() == null ? "0" : Integer.toString(runtime.cellX()), "cellZ", runtime.cellZ() == null ? "0" : Integer.toString(runtime.cellZ())), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name(), "targetNode", node.nodeId()));
        return new Result(true, "ACTIVATING", runtime);
    }

    public Result deactivate(UUID islandId) {
        IslandRuntimeSnapshot runtime = runtimes.markSaving(islandId);
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.DEACTIVATE_ISLAND, islandId, runtime.activeNode(), 0, Map.of(), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_DEACTIVATED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name()));
        return new Result(true, "SAVING", runtime);
    }

    public Result migrate(UUID islandId, String targetNode) {
        IslandRuntimeSnapshot runtime = runtimes.markMigrating(islandId, targetNode);
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.MIGRATE_ISLAND, islandId, targetNode, 10, Map.of("fencingToken", Long.toString(runtime.fencingToken()), "worldName", runtime.activeWorld() == null ? "ci_shard_001" : runtime.activeWorld(), "cellX", runtime.cellX() == null ? "0" : Integer.toString(runtime.cellX()), "cellZ", runtime.cellZ() == null ? "0" : Integer.toString(runtime.cellZ())), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_MIGRATED.name(), Map.of("islandId", islandId.toString(), "targetNode", targetNode, "fencingToken", Long.toString(runtime.fencingToken())));
        return new Result(true, "MIGRATING", runtime);
    }

    public Result snapshot(UUID islandId, String reason) {
        IslandRuntimeSnapshot runtime = runtimes.find(islandId).orElseGet(() -> runtimes.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.INACTIVE_READY));
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.SNAPSHOT_ISLAND, islandId, runtime.activeNode(), 20, Map.of("reason", reason), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), Map.of("islandId", islandId.toString(), "reason", reason));
        return new Result(true, "SNAPSHOT_QUEUED", runtime);
    }

    public Result restore(UUID islandId, long snapshotNo) {
        NodeLoad node = allocator.selectBestNode(nodes.snapshot(), Instant.now()).orElse(null);
        if (node == null) {
            return new Result(false, "NODE_UNAVAILABLE", null);
        }
        IslandRuntimeSnapshot runtime = runtimes.markActivating(islandId, node.nodeId(), "ci_shard_001", 0, 0);
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.RESTORE_ISLAND, islandId, node.nodeId(), 30, Map.of("snapshotNo", Long.toString(snapshotNo), "fencingToken", Long.toString(runtime.fencingToken())), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", "RESTORING", "snapshotNo", Long.toString(snapshotNo)));
        return new Result(true, "RESTORE_QUEUED", runtime);
    }

    public Result quarantine(UUID islandId, String reason) {
        IslandRuntimeSnapshot runtime = runtimes.markQuarantined(islandId, reason);
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name(), "reason", reason));
        return new Result(true, "QUARANTINED", runtime);
    }

    public record Result(boolean accepted, String code, IslandRuntimeSnapshot runtime) {}
}
