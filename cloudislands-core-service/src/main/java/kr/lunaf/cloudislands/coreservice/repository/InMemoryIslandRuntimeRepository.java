package kr.lunaf.cloudislands.coreservice.repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.runtime.IslandRuntimeStatePolicy;

public final class InMemoryIslandRuntimeRepository implements IslandRuntimeRepository {
    private final Map<UUID, IslandRuntimeSnapshot> runtimes = new ConcurrentHashMap<>();

    @Override
    public Optional<IslandRuntimeSnapshot> find(UUID islandId) {
        return Optional.ofNullable(runtimes.get(islandId));
    }

    @Override
    public List<IslandRuntimeSnapshot> listByNode(String nodeId, int limit) {
        int cappedLimit = limit == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, Math.min(limit, 200));
        return runtimes.values().stream()
            .filter(runtime -> nodeId != null && nodeId.equals(runtime.activeNode()))
            .filter(this::listedByNode)
            .limit(cappedLimit)
            .toList();
    }

    @Override
    public boolean placementOccupied(String worldName, int cellX, int cellZ, UUID exceptIslandId) {
        return runtimes.values().stream()
            .filter(this::occupiesPlacement)
            .filter(runtime -> exceptIslandId == null || !exceptIslandId.equals(runtime.islandId()))
            .anyMatch(runtime -> worldName != null
                && worldName.equals(runtime.activeWorld())
                && runtime.cellX() != null
                && runtime.cellZ() != null
                && runtime.cellX() == cellX
                && runtime.cellZ() == cellZ);
    }

    @Override
    public synchronized IslandRuntimeSnapshot markActivating(UUID islandId, String targetNode, String targetWorld, int cellX, int cellZ) {
        IslandRuntimeSnapshot current = find(islandId).orElse(null);
        rejectDuplicateActivation(current);
        rejectSplitBrainActivation(current, targetNode);
        long nextToken = IslandRuntimeStatePolicy.nextFencingToken(current);
        return put(new IslandRuntimeSnapshot(islandId, IslandState.ACTIVATING, targetNode, targetWorld, cellX, cellZ, targetNode, nextToken, null, Instant.now()));
    }

    @Override
    public synchronized IslandRuntimeSnapshot markActive(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, long fencingToken) {
        IslandRuntimeSnapshot current = find(islandId).orElse(null);
        if (IslandRuntimeStatePolicy.staleFencingToken(current, fencingToken)) {
            return current;
        }
        rejectSplitBrainActivation(current, nodeId);
        return put(new IslandRuntimeSnapshot(islandId, IslandState.ACTIVE, nodeId, worldName, cellX, cellZ, nodeId, fencingToken, Instant.now(), Instant.now()));
    }

    @Override
    public IslandRuntimeSnapshot markSaving(UUID islandId) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return put(new IslandRuntimeSnapshot(islandId, IslandState.SAVING, current.activeNode(), current.activeWorld(), current.cellX(), current.cellZ(), current.leaseOwner(), current.fencingToken(), current.activatedAt(), Instant.now()));
    }

    @Override
    public synchronized IslandRuntimeSnapshot markSaving(UUID islandId, long fencingToken) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        if (IslandRuntimeStatePolicy.staleFencingToken(current, fencingToken)) {
            return current;
        }
        return put(new IslandRuntimeSnapshot(islandId, IslandState.SAVING, current.activeNode(), current.activeWorld(), current.cellX(), current.cellZ(), current.leaseOwner(), fencingToken, current.activatedAt(), Instant.now()));
    }

    @Override
    public IslandRuntimeSnapshot markInactive(UUID islandId) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return put(new IslandRuntimeSnapshot(islandId, IslandState.INACTIVE_READY, null, null, null, null, null, current.fencingToken(), null, Instant.now()));
    }

    @Override
    public IslandRuntimeSnapshot markInactive(UUID islandId, long fencingToken) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        if (current.fencingToken() > fencingToken) {
            return current;
        }
        return put(new IslandRuntimeSnapshot(islandId, IslandState.INACTIVE_READY, null, null, null, null, null, fencingToken, null, Instant.now()));
    }

    @Override
    public IslandRuntimeSnapshot markMigrating(UUID islandId, String targetNode) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return put(new IslandRuntimeSnapshot(islandId, IslandState.DEACTIVATING, targetNode, current.activeWorld(), current.cellX(), current.cellZ(), targetNode, current.fencingToken() + 1L, current.activatedAt(), Instant.now()));
    }

    @Override
    public IslandRuntimeSnapshot markQuarantined(UUID islandId, String reason) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        return put(new IslandRuntimeSnapshot(islandId, IslandState.QUARANTINED, current.activeNode(), current.activeWorld(), current.cellX(), current.cellZ(), current.leaseOwner(), current.fencingToken(), current.activatedAt(), Instant.now()));
    }

    @Override
    public IslandRuntimeSnapshot setState(UUID islandId, IslandState state) {
        IslandRuntimeSnapshot current = find(islandId).orElse(defaultRuntime(islandId));
        rejectIllegalTransition(islandId, current.state(), state);
        return put(new IslandRuntimeSnapshot(islandId, state, current.activeNode(), current.activeWorld(), current.cellX(), current.cellZ(), current.leaseOwner(), current.fencingToken(), current.activatedAt(), Instant.now()));
    }

    @Override
    public Map<String, Long> countsByState() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (IslandState state : IslandState.values()) {
            counts.put(state.name(), 0L);
        }
        for (IslandRuntimeSnapshot runtime : runtimes.values()) {
            counts.compute(runtime.state().name(), (state, total) -> total == null ? 1L : total + 1L);
        }
        return counts;
    }

    @Override
    public int markRecoveryRequiredForNode(String nodeId) {
        int changed = 0;
        for (IslandRuntimeSnapshot runtime : runtimes.values()) {
            if (!nodeId.equals(runtime.activeNode())) {
                continue;
            }
            if (!IslandRuntimeStatePolicy.runningOnNode(runtime)) {
                continue;
            }
            put(new IslandRuntimeSnapshot(runtime.islandId(), IslandState.RECOVERY_REQUIRED, runtime.activeNode(), runtime.activeWorld(), runtime.cellX(), runtime.cellZ(), runtime.leaseOwner(), runtime.fencingToken(), runtime.activatedAt(), Instant.now()));
            changed++;
        }
        return changed;
    }

    private IslandRuntimeSnapshot put(IslandRuntimeSnapshot runtime) {
        runtimes.put(runtime.islandId(), runtime);
        return runtime;
    }

    private boolean runningOnNode(IslandRuntimeSnapshot runtime) {
        return IslandRuntimeStatePolicy.runningOnNode(runtime);
    }

    private void rejectSplitBrainActivation(IslandRuntimeSnapshot current, String targetNode) {
        if (current == null || !runningOnNode(current)) {
            return;
        }
        String activeNode = current.activeNode();
        if (activeNode == null || activeNode.isBlank() || activeNode.equals(targetNode)) {
            return;
        }
        throw new IllegalStateException("split-brain activation rejected for island " + current.islandId()
            + ": activeNode=" + activeNode + ", targetNode=" + targetNode + ", state=" + current.state());
    }

    private void rejectDuplicateActivation(IslandRuntimeSnapshot current) {
        if (runningOnNode(current)) {
            throw new IllegalStateException("ACTIVATION_LOCKED");
        }
    }

    private void rejectIllegalTransition(UUID islandId, IslandState from, IslandState to) {
        if (IslandRuntimeStatePolicy.transitionAllowed(from, to)) {
            return;
        }
        throw new IllegalStateException("illegal island runtime transition for island " + islandId + ": " + from + " -> " + to);
    }

    private boolean listedByNode(IslandRuntimeSnapshot runtime) {
        return IslandRuntimeStatePolicy.listedByNode(runtime);
    }

    private boolean occupiesPlacement(IslandRuntimeSnapshot runtime) {
        return IslandRuntimeStatePolicy.occupiesPlacement(runtime);
    }

    private IslandRuntimeSnapshot defaultRuntime(UUID islandId) {
        return new IslandRuntimeSnapshot(islandId, IslandState.INACTIVE_READY, null, null, null, null, null, 0L, null, Instant.now());
    }
}
