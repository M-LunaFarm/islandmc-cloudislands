package kr.lunaf.cloudislands.coreservice.warehouse;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;

public final class InMemoryIslandWarehouseRepository implements IslandWarehouseRepository {
    private final Map<UUID, Map<String, IslandWarehouseItemSnapshot>> items = new ConcurrentHashMap<>();

    @Override
    public synchronized ChangeResult deposit(UUID islandId, String materialKey, long amount) {
        String key = IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey);
        if (amount <= 0L) {
            return new ChangeResult(false, "INVALID_AMOUNT", snapshot(islandId, key));
        }
        IslandWarehouseItemSnapshot current = snapshot(islandId, key);
        IslandWarehouseItemSnapshot updated = new IslandWarehouseItemSnapshot(islandId, key, Math.addExact(current.amount(), amount), Instant.now());
        items.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(key, updated);
        return new ChangeResult(true, "DEPOSITED", updated);
    }

    @Override
    public synchronized ChangeResult withdraw(UUID islandId, String materialKey, long amount) {
        String key = IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey);
        IslandWarehouseItemSnapshot current = snapshot(islandId, key);
        if (amount <= 0L) {
            return new ChangeResult(false, "INVALID_AMOUNT", current);
        }
        if (current.amount() < amount) {
            return new ChangeResult(false, "INSUFFICIENT_ITEMS", current);
        }
        IslandWarehouseItemSnapshot updated = new IslandWarehouseItemSnapshot(islandId, key, current.amount() - amount, Instant.now());
        items.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(key, updated);
        return new ChangeResult(true, "WITHDRAWN", updated);
    }

    @Override
    public List<IslandWarehouseItemSnapshot> list(UUID islandId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        return items.getOrDefault(islandId, Map.of()).values().stream()
            .filter(item -> item.amount() > 0L)
            .sorted(Comparator.comparing(IslandWarehouseItemSnapshot::amount).reversed().thenComparing(IslandWarehouseItemSnapshot::materialKey))
            .limit(cappedLimit)
            .toList();
    }

    private IslandWarehouseItemSnapshot snapshot(UUID islandId, String materialKey) {
        return items.getOrDefault(islandId, Map.of()).getOrDefault(materialKey, new IslandWarehouseItemSnapshot(islandId, materialKey, 0L, Instant.EPOCH));
    }
}
