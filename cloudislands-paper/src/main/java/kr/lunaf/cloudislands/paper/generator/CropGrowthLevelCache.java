package kr.lunaf.cloudislands.paper.generator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

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
        client.listIslandUpgrades(islandId)
            .thenAccept(body -> cache.put(islandId, new CachedLevel(parseCropLevel(body), System.currentTimeMillis() + TTL_MILLIS)))
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

    private int parseCropLevel(String json) {
        if (json == null || json.isBlank()) {
            return 1;
        }
        for (Object item : SimpleJson.list(SimpleJson.parse(json))) {
            Map<?, ?> object = SimpleJson.object(item);
            if ("crop".equalsIgnoreCase(SimpleJson.text(object.get("upgradeKey")))) {
                return Math.max(1, (int) SimpleJson.number(object.get("level")));
            }
        }
        return 1;
    }

    private record CachedLevel(int level, long expiresAtMillis) {}
}
