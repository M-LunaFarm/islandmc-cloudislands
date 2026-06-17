package kr.lunaf.cloudislands.paper.activation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActiveIslandRegistry {
    private final Map<UUID, ActiveIsland> active = new ConcurrentHashMap<>();

    public boolean acceptsActivation(UUID islandId, long fencingToken) {
        ActiveIsland current = active.get(islandId);
        return current == null || current.fencingToken() <= fencingToken;
    }

    public boolean activated(IslandActivationJobHandler.ActivationResult result) {
        if (!result.success() || result.islandId() == null) {
            return false;
        }
        ActiveIsland next = new ActiveIsland(result.islandId(), result.worldName(), result.cellX(), result.cellZ(), result.originX(), result.originZ(), result.islandSize(), result.schemaVersion(), result.fencingToken(), Instant.now());
        ActiveIsland stored = active.compute(result.islandId(), (_islandId, current) -> current != null && current.fencingToken() > result.fencingToken() ? current : next);
        return stored == next;
    }

    public Optional<ActiveIsland> find(UUID islandId) {
        return Optional.ofNullable(active.get(islandId));
    }

    public void deactivated(UUID islandId) {
        active.remove(islandId);
    }

    public int size() {
        return active.size();
    }

    public List<ActiveIsland> snapshot() {
        return List.copyOf(active.values());
    }

    public record ActiveIsland(UUID islandId, String worldName, int cellX, int cellZ, int originX, int originZ, int islandSize, long schemaVersion, long fencingToken, Instant activatedAt) {}
}
