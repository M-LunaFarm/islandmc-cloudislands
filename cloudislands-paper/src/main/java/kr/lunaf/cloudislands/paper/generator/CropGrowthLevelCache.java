package kr.lunaf.cloudislands.paper.generator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

public final class CropGrowthLevelCache {
    private static final long TTL_MILLIS = 30_000L;
    private final CoreApiClient client;
    private final Map<UUID, CachedLevel> cache = new ConcurrentHashMap<>();

    public CropGrowthLevelCache(CoreApiClient client) {
        this.client = client;
    }

    public int level(UUID islandId) {
        CachedLevel cached = cache.get(islandId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.level();
        }
        int fallback = cached == null ? 1 : cached.level();
        cache.put(islandId, new CachedLevel(fallback, now + 5_000L));
        client.progression().upgrades(islandId)
            .thenAccept(upgrades -> cache.put(islandId, new CachedLevel(cropLevel(upgrades), System.currentTimeMillis() + TTL_MILLIS)))
            .exceptionally(exception -> {
                cache.put(islandId, new CachedLevel(fallback, System.currentTimeMillis() + TTL_MILLIS));
                return null;
            });
        return fallback;
    }

    public void invalidate(UUID islandId) {
        cache.remove(islandId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private int cropLevel(java.util.List<CoreGuiViews.UpgradeView> upgrades) {
        if (upgrades == null || upgrades.isEmpty()) {
            return 1;
        }
        for (CoreGuiViews.UpgradeView upgrade : upgrades) {
            if (upgrade != null && "crop".equalsIgnoreCase(upgrade.key())) {
                return Math.max(1, upgrade.level());
            }
        }
        return 1;
    }

    private record CachedLevel(int level, long expiresAtMillis) {}
}
