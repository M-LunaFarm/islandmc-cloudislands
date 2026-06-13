package kr.lunaf.cloudislands.coreservice.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.RedisActivationLock;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class IslandLifecycleWorkflow {
    private final IslandRuntimeRepository runtimes;
    private final IslandRepository islands;
    private final IslandTemplateRepository templates;
    private final NodeRegistry nodes;
    private final NodeAllocator allocator;
    private final IslandJobPublisher jobs;
    private final GlobalEventPublisher events;
    private final String islandPool;
    private final String migrationPolicy;
    private final RedisActivationLock activationLock;

    public IslandLifecycleWorkflow(IslandRuntimeRepository runtimes, IslandRepository islands, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events) {
        this(runtimes, islands, templates, nodes, allocator, jobs, events, "island");
    }

    public IslandLifecycleWorkflow(IslandRuntimeRepository runtimes, IslandRepository islands, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, String islandPool) {
        this(runtimes, islands, templates, nodes, allocator, jobs, events, islandPool, "INACTIVE_ONLY_AUTOMATIC", null);
    }

    public IslandLifecycleWorkflow(IslandRuntimeRepository runtimes, IslandRepository islands, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, String islandPool, RedisActivationLock activationLock) {
        this(runtimes, islands, templates, nodes, allocator, jobs, events, islandPool, "INACTIVE_ONLY_AUTOMATIC", activationLock);
    }

    public IslandLifecycleWorkflow(IslandRuntimeRepository runtimes, IslandRepository islands, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, String islandPool, String migrationPolicy, RedisActivationLock activationLock) {
        this.runtimes = runtimes;
        this.islands = islands;
        this.templates = templates;
        this.nodes = nodes;
        this.allocator = allocator;
        this.jobs = jobs;
        this.events = events;
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.migrationPolicy = migrationPolicy == null || migrationPolicy.isBlank() ? "INACTIVE_ONLY_AUTOMATIC" : migrationPolicy;
        this.activationLock = activationLock;
    }

    public Result activate(UUID islandId) {
        IslandRuntimeSnapshot current = runtimes.find(islandId).orElse(null);
        if (current != null && current.state() == IslandState.ACTIVE) {
            return new Result(true, "ACTIVE", current);
        }
        if (!canStartActivation(current)) {
            return new Result(false, "ISLAND_BUSY", current);
        }
        String templateId = islands.templateId(islandId).orElse("default");
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        NodeLoad node = allocator.selectReadyNode(nodeSnapshot, Instant.now(), templateId, minNodeVersion(templateId), islandPool).orElse(null);
        if (node == null) {
            return new Result(false, readyNodeUnavailableCode(nodeSnapshot, templateId), null);
        }
        RedisActivationLock.Lease lease = acquireActivationLock(islandId, "activate");
        if (activationLock != null && lease == null) {
            return new Result(false, "ACTIVATION_LOCKED", current);
        }
        IslandRuntimeSnapshot runtime = runtimes.markActivating(islandId, node.nodeId(), "ci_shard_001", 0, 0);
        islands.setState(islandId, IslandState.ACTIVATING);
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.ACTIVATE_ISLAND, islandId, node.nodeId(), 0, Map.of("fencingToken", Long.toString(runtime.fencingToken()), "worldName", runtime.activeWorld() == null ? "ci_shard_001" : runtime.activeWorld(), "cellX", runtime.cellX() == null ? "0" : Integer.toString(runtime.cellX()), "cellZ", runtime.cellZ() == null ? "0" : Integer.toString(runtime.cellZ())), Instant.now()));
        } catch (RuntimeException exception) {
            releaseActivationLock(lease);
            return jobQueueFailed(islandId, IslandState.ERROR_ACTIVATING);
        }
        events.publish(CloudIslandEventType.ISLAND_ACTIVATE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name(), "targetNode", node.nodeId()));
        return new Result(true, "ACTIVATING", runtime);
    }

    public Result activationPreflight(UUID islandId) {
        IslandRuntimeSnapshot current = runtimes.find(islandId).orElse(null);
        if (current != null && current.state() == IslandState.ACTIVE) {
            return new Result(true, "ACTIVE", current);
        }
        if (!canStartActivation(current)) {
            return new Result(false, "ISLAND_BUSY", current);
        }
        String templateId = islands.templateId(islandId).orElse("default");
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        NodeLoad node = allocator.selectReadyNode(nodeSnapshot, Instant.now(), templateId, minNodeVersion(templateId), islandPool).orElse(null);
        if (node == null) {
            return new Result(false, readyNodeUnavailableCode(nodeSnapshot, templateId), current);
        }
        return new Result(true, "ACTIVATION_READY", current);
    }

    public Result deactivate(UUID islandId) {
        IslandRuntimeSnapshot current = runtimes.find(islandId).orElse(null);
        if (current == null || current.activeNode() == null || current.activeNode().isBlank()) {
            return new Result(false, "ISLAND_NOT_ACTIVE", current);
        }
        if (current.state() == IslandState.SAVING || current.state() == IslandState.DEACTIVATING) {
            return new Result(true, current.state().name(), current);
        }
        if (current.state() != IslandState.ACTIVE) {
            return new Result(false, "ISLAND_BUSY", current);
        }
        IslandRuntimeSnapshot runtime = runtimes.markSaving(islandId);
        islands.setState(islandId, IslandState.SAVING);
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.DEACTIVATE_ISLAND, islandId, runtime.activeNode(), 0, Map.of("fencingToken", Long.toString(runtime.fencingToken())), Instant.now()));
        } catch (RuntimeException exception) {
            return jobQueueFailed(islandId, IslandState.ERROR_SAVING);
        }
        events.publish(CloudIslandEventType.ISLAND_DEACTIVATE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name()));
        return new Result(true, "SAVING", runtime);
    }

    public Result migrate(UUID islandId, String targetNode) {
        IslandRuntimeSnapshot current = runtimes.find(islandId).orElse(null);
        if (migrationDisabled()) {
            return new Result(false, "MIGRATION_DISABLED", current);
        }
        if (current != null && current.state() == IslandState.ACTIVE && targetNode != null && targetNode.equals(current.activeNode())) {
            return new Result(true, "ALREADY_ON_NODE", current);
        }
        if (current != null && current.state() == IslandState.ACTIVE && !activeMigrationAllowed()) {
            return new Result(false, "ACTIVE_MIGRATION_DISABLED", current);
        }
        if (!canStartActivation(current) && (current == null || current.state() != IslandState.ACTIVE)) {
            return new Result(false, "ISLAND_BUSY", current);
        }
        String templateId = islands.templateId(islandId).orElse("default");
        NodeLoad node = nodes.find(targetNode).orElse(null);
        if (node == null || allocator.selectBestNode(java.util.List.of(node), Instant.now(), templateId, minNodeVersion(templateId), islandPool).isEmpty()) {
            return new Result(false, targetNodeUnavailableCode(node, templateId), null);
        }
        RedisActivationLock.Lease lease = acquireActivationLock(islandId, "migrate");
        if (activationLock != null && lease == null) {
            return new Result(false, "ACTIVATION_LOCKED", current);
        }
        IslandRuntimeSnapshot runtime = runtimes.markMigrating(islandId, targetNode);
        islands.setState(islandId, IslandState.DEACTIVATING);
        String sourceNode = current == null ? "" : current.activeNode();
        try {
            if (sourceNode != null && !sourceNode.isBlank() && !sourceNode.equals(targetNode)) {
                jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.DEACTIVATE_ISLAND, islandId, sourceNode, 10, Map.of("reason", "BEFORE_MIGRATION", "migrateTargetNode", targetNode, "fencingToken", Long.toString(runtime.fencingToken()), "migrationFencingToken", Long.toString(runtime.fencingToken())), Instant.now()));
            } else {
                jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.MIGRATE_ISLAND, islandId, targetNode, 10, Map.of("fencingToken", Long.toString(runtime.fencingToken()), "worldName", runtime.activeWorld() == null ? "ci_shard_001" : runtime.activeWorld(), "cellX", runtime.cellX() == null ? "0" : Integer.toString(runtime.cellX()), "cellZ", runtime.cellZ() == null ? "0" : Integer.toString(runtime.cellZ())), Instant.now()));
            }
        } catch (RuntimeException exception) {
            releaseActivationLock(lease);
            return jobQueueFailed(islandId, IslandState.ERROR_ACTIVATING);
        }
        events.publish(CloudIslandEventType.ISLAND_MIGRATE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "targetNode", targetNode, "fencingToken", Long.toString(runtime.fencingToken())));
        return new Result(true, "MIGRATING", runtime);
    }

    private boolean migrationDisabled() {
        return migrationPolicy.equalsIgnoreCase("DISABLED")
            || migrationPolicy.equalsIgnoreCase("NONE");
    }

    private boolean activeMigrationAllowed() {
        return !migrationPolicy.equalsIgnoreCase("INACTIVE_ONLY")
            && !migrationPolicy.equalsIgnoreCase("INACTIVE_ONLY_MANUAL");
    }

    public Result snapshot(UUID islandId, String reason) {
        IslandRuntimeSnapshot runtime = runtimes.find(islandId).orElseGet(() -> runtimes.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.INACTIVE_READY));
        if (runtime.activeNode() == null || runtime.activeNode().isBlank()) {
            return new Result(false, "ISLAND_NOT_ACTIVE", runtime);
        }
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.SNAPSHOT_ISLAND, islandId, runtime.activeNode(), 20, Map.of("reason", reason, "fencingToken", Long.toString(runtime.fencingToken())), Instant.now()));
        } catch (RuntimeException exception) {
            return jobQueueFailed(islandId, IslandState.ERROR_SAVING);
        }
        events.publish(CloudIslandEventType.ISLAND_SNAPSHOT_REQUESTED.name(), Map.of("islandId", islandId.toString(), "reason", reason));
        return new Result(true, "SNAPSHOT_QUEUED", runtime);
    }

    public Result restore(UUID islandId, long snapshotNo) {
        return restore(islandId, snapshotNo, "");
    }

    public Result restore(UUID islandId, long snapshotNo, String storagePath) {
        IslandRuntimeSnapshot current = runtimes.find(islandId).orElse(null);
        if (current != null && current.state() == IslandState.ACTIVE) {
            return restoreActive(islandId, current, snapshotNo, storagePath);
        }
        if (!canStartActivation(current)) {
            return new Result(false, "ISLAND_BUSY", current);
        }
        String templateId = islands.templateId(islandId).orElse("default");
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        NodeLoad node = allocator.selectReadyNode(nodeSnapshot, Instant.now(), templateId, minNodeVersion(templateId), islandPool).orElse(null);
        if (node == null) {
            return new Result(false, readyNodeUnavailableCode(nodeSnapshot, templateId), null);
        }
        RedisActivationLock.Lease lease = acquireActivationLock(islandId, "restore");
        if (activationLock != null && lease == null) {
            return new Result(false, "ACTIVATION_LOCKED", current);
        }
        IslandRuntimeSnapshot runtime = runtimes.markActivating(islandId, node.nodeId(), "ci_shard_001", 0, 0);
        islands.setState(islandId, IslandState.ACTIVATING);
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.RESTORE_ISLAND, islandId, node.nodeId(), 30, Map.of("snapshotNo", Long.toString(snapshotNo), "storagePath", storagePath == null ? "" : storagePath, "fencingToken", Long.toString(runtime.fencingToken())), Instant.now()));
        } catch (RuntimeException exception) {
            releaseActivationLock(lease);
            return jobQueueFailed(islandId, IslandState.ERROR_ACTIVATING);
        }
        events.publish(CloudIslandEventType.ISLAND_RESTORE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "state", "RESTORING", "snapshotNo", Long.toString(snapshotNo)));
        return new Result(true, "RESTORE_QUEUED", runtime);
    }

    public Result reset(UUID islandId, String reason) {
        IslandRuntimeSnapshot current = runtimes.find(islandId).orElse(null);
        if (current != null && current.state() == IslandState.ACTIVE) {
            return resetActive(islandId, current, reason);
        }
        if (!canStartActivation(current)) {
            return new Result(false, "ISLAND_BUSY", current);
        }
        String templateId = islands.templateId(islandId).orElse("default");
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        NodeLoad node = allocator.selectReadyNode(nodeSnapshot, Instant.now(), templateId, minNodeVersion(templateId), islandPool).orElse(null);
        if (node == null) {
            return new Result(false, readyNodeUnavailableCode(nodeSnapshot, templateId), null);
        }
        RedisActivationLock.Lease lease = acquireActivationLock(islandId, "reset");
        if (activationLock != null && lease == null) {
            return new Result(false, "ACTIVATION_LOCKED", current);
        }
        IslandRuntimeSnapshot runtime = runtimes.markActivating(islandId, node.nodeId(), "ci_shard_001", 0, 0);
        islands.setState(islandId, IslandState.ACTIVATING);
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.RESET_ISLAND, islandId, node.nodeId(), 40, Map.of("templateId", templateId, "reason", reason, "fencingToken", Long.toString(runtime.fencingToken())), Instant.now()));
        } catch (RuntimeException exception) {
            releaseActivationLock(lease);
            return jobQueueFailed(islandId, IslandState.ERROR_ACTIVATING);
        }
        events.publish(CloudIslandEventType.ISLAND_RESET_REQUESTED.name(), Map.of("islandId", islandId.toString(), "state", "RESETTING", "targetNode", node.nodeId(), "reason", reason));
        return new Result(true, "RESET_QUEUED", runtime);
    }

    public Result quarantine(UUID islandId, String reason) {
        IslandRuntimeSnapshot runtime = runtimes.markQuarantined(islandId, reason);
        islands.setState(islandId, IslandState.QUARANTINED);
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name(), "reason", reason));
        return new Result(true, "QUARANTINED", runtime);
    }

    private Result restoreActive(UUID islandId, IslandRuntimeSnapshot current, long snapshotNo, String storagePath) {
        if (current.activeNode() == null || current.activeNode().isBlank() || nodes.find(current.activeNode()).isEmpty()) {
            return new Result(false, "NODE_UNAVAILABLE", current);
        }
        IslandRuntimeSnapshot runtime = runtimes.setState(islandId, IslandState.ACTIVATING);
        islands.setState(islandId, IslandState.ACTIVATING);
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.RESTORE_ISLAND, islandId, current.activeNode(), 30, Map.of(
                "snapshotNo", Long.toString(snapshotNo),
                "storagePath", storagePath == null ? "" : storagePath,
                "fencingToken", Long.toString(current.fencingToken()),
                "worldName", current.activeWorld() == null ? "ci_shard_001" : current.activeWorld(),
                "cellX", current.cellX() == null ? "0" : Integer.toString(current.cellX()),
                "cellZ", current.cellZ() == null ? "0" : Integer.toString(current.cellZ())
            ), Instant.now()));
        } catch (RuntimeException exception) {
            return jobQueueFailed(islandId, IslandState.ERROR_ACTIVATING);
        }
        events.publish(CloudIslandEventType.ISLAND_RESTORE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "state", "RESTORING_ACTIVE", "snapshotNo", Long.toString(snapshotNo), "targetNode", current.activeNode()));
        return new Result(true, "RESTORE_QUEUED", runtime);
    }

    private Result resetActive(UUID islandId, IslandRuntimeSnapshot current, String reason) {
        if (current.activeNode() == null || current.activeNode().isBlank() || nodes.find(current.activeNode()).isEmpty()) {
            return new Result(false, "NODE_UNAVAILABLE", current);
        }
        String templateId = islands.templateId(islandId).orElse("default");
        IslandRuntimeSnapshot runtime = runtimes.setState(islandId, IslandState.ACTIVATING);
        islands.setState(islandId, IslandState.ACTIVATING);
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.RESET_ISLAND, islandId, current.activeNode(), 40, Map.of(
                "templateId", templateId,
                "reason", reason,
                "fencingToken", Long.toString(current.fencingToken()),
                "worldName", current.activeWorld() == null ? "ci_shard_001" : current.activeWorld(),
                "cellX", current.cellX() == null ? "0" : Integer.toString(current.cellX()),
                "cellZ", current.cellZ() == null ? "0" : Integer.toString(current.cellZ())
            ), Instant.now()));
        } catch (RuntimeException exception) {
            return jobQueueFailed(islandId, IslandState.ERROR_ACTIVATING);
        }
        events.publish(CloudIslandEventType.ISLAND_RESET_REQUESTED.name(), Map.of("islandId", islandId.toString(), "state", "RESETTING_ACTIVE", "targetNode", current.activeNode(), "reason", reason));
        return new Result(true, "RESET_QUEUED", runtime);
    }

    private boolean canStartActivation(IslandRuntimeSnapshot runtime) {
        if (runtime == null) {
            return true;
        }
        if (runtime.activeNode() != null && !runtime.activeNode().isBlank()) {
            return false;
        }
        return runtime.state() == IslandState.INACTIVE_READY
            || runtime.state() == IslandState.ERROR_CREATING
            || runtime.state() == IslandState.ERROR_ACTIVATING
            || runtime.state() == IslandState.ERROR_SAVING;
    }

    private String readyNodeUnavailableCode(List<NodeLoad> nodeSnapshot, String templateId) {
        String reason = allocator.readyNodeBlockReason(nodeSnapshot, Instant.now(), templateId, minNodeVersion(templateId), islandPool);
        return "NO_READY_NODE".equals(reason) ? "NO_READY_NODE" : "NO_READY_NODE_" + reason;
    }

    private String targetNodeUnavailableCode(NodeLoad node, String templateId) {
        String blockReason = allocator.nodeBlockReason(node, Instant.now(), templateId, minNodeVersion(templateId), islandPool);
        if ("NODE_NOT_FOUND".equals(blockReason)) {
            return "TARGET_NODE_NOT_FOUND";
        }
        if ("NODE_VERSION_TOO_OLD".equals(blockReason)) {
            return "TARGET_NODE_VERSION_TOO_OLD";
        }
        return blockReason.isBlank() ? "TARGET_NODE_NOT_READY" : "TARGET_NODE_" + blockReason;
    }

    public record Result(boolean accepted, String code, IslandRuntimeSnapshot runtime) {}

    private Result jobQueueFailed(UUID islandId, IslandState state) {
        IslandRuntimeSnapshot runtime = runtimes.setState(islandId, state);
        islands.setState(islandId, state);
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", state.name(), "reason", "JOB_QUEUE_UNAVAILABLE"));
        return new Result(false, "JOB_QUEUE_UNAVAILABLE", runtime);
    }

    private String minNodeVersion(String templateId) {
        return templates.find(templateId).map(kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot::minNodeVersion).orElse("");
    }

    private RedisActivationLock.Lease acquireActivationLock(UUID islandId, String owner) {
        return activationLock == null ? null : activationLock.acquire(islandId, owner).orElse(null);
    }

    private void releaseActivationLock(RedisActivationLock.Lease lease) {
        if (activationLock != null) {
            activationLock.release(lease);
        }
    }
}
