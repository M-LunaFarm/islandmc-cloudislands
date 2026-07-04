package kr.seungmin.satisskyfactory.storage;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SatisRuntimeAuthority {
    private final String localNodeId;
    private final Map<UUID, RuntimeAuthoritySnapshot> snapshots = new ConcurrentHashMap<>();

    public SatisRuntimeAuthority(String localNodeId) {
        this.localNodeId = normalize(localNodeId);
    }

    public boolean localAuthorityKnown() {
        return !localNodeId.isBlank();
    }

    public String localNodeId() {
        return localNodeId.isBlank() ? "unknown" : localNodeId;
    }

    public void activated(UUID islandId, String nodeId, String worldName, int cellX, int cellZ) {
        update(islandId, "ACTIVE", nodeId, worldName, cellX, cellZ, false);
    }

    public void runtimeChanged(UUID islandId, String state, String targetNode) {
        String normalizedState = normalizeState(state);
        if (islandId == null) {
            return;
        }
        if (recoveryState(normalizedState)) {
            snapshots.put(islandId, new RuntimeAuthoritySnapshot(islandId, normalizedState, normalize(targetNode), "", 0, 0, false, true, Instant.now()));
            return;
        }
        if ("ACTIVE".equals(normalizedState)) {
            RuntimeAuthoritySnapshot previous = snapshots.get(islandId);
            update(islandId, normalizedState, targetNode, previous == null ? "" : previous.activeWorld(), previous == null ? 0 : previous.cellX(), previous == null ? 0 : previous.cellZ(), false);
            return;
        }
        if ("SAVING".equals(normalizedState) || "DEACTIVATING".equals(normalizedState) || "DEACTIVATED".equals(normalizedState)) {
            snapshots.remove(islandId);
        }
    }

    public void deactivated(UUID islandId) {
        if (islandId != null) {
            snapshots.remove(islandId);
        }
    }

    public void suspend(UUID islandId, String state, String nodeId) {
        if (islandId != null) {
            snapshots.put(islandId, new RuntimeAuthoritySnapshot(islandId, normalizeState(state), normalize(nodeId), "", 0, 0, false, true, Instant.now()));
        }
    }

    public boolean canTick(UUID islandId) {
        return canWrite(islandId);
    }

    public boolean canWrite(UUID islandId) {
        RuntimeAuthoritySnapshot snapshot = snapshots.get(islandId);
        return snapshot != null && snapshot.localOwner() && !snapshot.suspended() && "ACTIVE".equals(snapshot.state());
    }

    public RuntimeAuthoritySnapshot snapshot(UUID islandId) {
        return snapshots.get(islandId);
    }

    public int activeLocalIslands() {
        return (int) snapshots.values().stream().filter(RuntimeAuthoritySnapshot::localOwner).filter(snapshot -> !snapshot.suspended()).count();
    }

    public int trackedIslands() {
        return snapshots.size();
    }

    private void update(UUID islandId, String state, String nodeId, String worldName, int cellX, int cellZ, boolean suspended) {
        if (islandId == null) {
            return;
        }
        String activeNode = normalize(nodeId);
        boolean localOwner = localAuthorityKnown() && localNodeId.equals(activeNode);
        snapshots.put(islandId, new RuntimeAuthoritySnapshot(islandId, normalizeState(state), activeNode, worldName == null ? "" : worldName, cellX, cellZ, localOwner, suspended, Instant.now()));
    }

    private static boolean recoveryState(String state) {
        return "RECOVERY_REQUIRED".equals(state) || "QUARANTINED".equals(state);
    }

    private static String normalizeState(String state) {
        return state == null || state.isBlank() ? "UNKNOWN" : state.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalize(String nodeId) {
        return nodeId == null ? "" : nodeId.trim();
    }

    public record RuntimeAuthoritySnapshot(UUID islandId, String state, String activeNode, String activeWorld,
                                           int cellX, int cellZ, boolean localOwner, boolean suspended,
                                           Instant confirmedAt) {
    }
}
