package kr.lunaf.cloudislands.paper.activation;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActiveIslandRegistry {
    private final Map<UUID, ActiveIsland> active = new ConcurrentHashMap<>();

    public void activated(IslandActivationJobHandler.ActivationResult result) {
        if (result.success() && result.islandId() != null) {
            active.put(result.islandId(), new ActiveIsland(result.islandId(), result.worldName(), result.cellX(), result.cellZ(), result.originX(), result.originZ(), result.islandSize(), result.schemaVersion(), Instant.now()));
        }
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

    public record ActiveIsland(UUID islandId, String worldName, int cellX, int cellZ, int originX, int originZ, int islandSize, long schemaVersion, Instant activatedAt) {}
}
