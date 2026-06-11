package kr.lunaf.cloudislands.coreservice.upgrade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public final class InMemoryIslandUpgradeRepository implements IslandUpgradeRepository {
    private final Map<UUID, Map<String, IslandUpgradeSnapshot>> upgrades = new ConcurrentHashMap<>();

    @Override
    public Optional<IslandUpgradeSnapshot> find(UUID islandId, String upgradeKey) {
        return Optional.ofNullable(upgrades.getOrDefault(islandId, Map.of()).get(upgradeKey));
    }

    @Override
    public List<IslandUpgradeSnapshot> list(UUID islandId) {
        return new ArrayList<>(upgrades.getOrDefault(islandId, Map.of()).values());
    }

    @Override
    public IslandUpgradeSnapshot setLevel(UUID islandId, String upgradeKey, UpgradeType type, int level) {
        IslandUpgradeSnapshot snapshot = new IslandUpgradeSnapshot(islandId, upgradeKey, type, level, Instant.now());
        upgrades.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(upgradeKey, snapshot);
        return snapshot;
    }
}
