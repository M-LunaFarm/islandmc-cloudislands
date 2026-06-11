package kr.lunaf.cloudislands.coreservice.limit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;

public final class InMemoryIslandLimitRepository implements IslandLimitRepository {
    private final Map<UUID, Map<String, IslandLimitSnapshot>> limits = new ConcurrentHashMap<>();

    @Override
    public java.util.List<IslandLimitSnapshot> list(UUID islandId) {
        seedDefaults(islandId);
        ArrayList<IslandLimitSnapshot> result = new ArrayList<>(limits.getOrDefault(islandId, Map.of()).values());
        result.sort(Comparator.comparing(IslandLimitSnapshot::limitKey));
        return java.util.List.copyOf(result);
    }

    @Override
    public IslandLimitSnapshot set(UUID islandId, String limitKey, long value, UUID updatedBy) {
        String key = normalize(limitKey);
        IslandLimitSnapshot snapshot = new IslandLimitSnapshot(islandId, key, Math.max(0L, value), updatedBy, Instant.now());
        limits.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(key, snapshot);
        return snapshot;
    }

    private void seedDefaults(UUID islandId) {
        Map<String, IslandLimitSnapshot> islandLimits = limits.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>());
        putDefault(islandLimits, islandId, "HOPPER", 50L);
        putDefault(islandLimits, islandId, "SPAWNER", 25L);
        putDefault(islandLimits, islandId, "ENTITY", 200L);
        putDefault(islandLimits, islandId, "REDSTONE", 512L);
    }

    private void putDefault(Map<String, IslandLimitSnapshot> islandLimits, UUID islandId, String key, long value) {
        islandLimits.putIfAbsent(key, new IslandLimitSnapshot(islandId, key, value, new UUID(0L, 0L), Instant.EPOCH));
    }

    private String normalize(String limitKey) {
        return limitKey == null || limitKey.isBlank() ? "HOPPER" : limitKey.toUpperCase();
    }
}
