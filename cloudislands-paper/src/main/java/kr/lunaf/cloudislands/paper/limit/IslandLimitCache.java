package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

public final class IslandLimitCache {
    private static final long TTL_MILLIS = 30_000L;
    private final CoreApiClient client;
    private final Map<UUID, CachedLimits> cache = new ConcurrentHashMap<>();

    public IslandLimitCache(CoreApiClient client) {
        this.client = client;
    }

    public long limit(UUID islandId, String limitKey, long fallback) {
        CachedLimits cached = cache.get(islandId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.value(limitKey, fallback);
        }
        Map<String, Long> fallbackValues = cached == null ? Map.of() : cached.values();
        cache.put(islandId, new CachedLimits(fallbackValues, now + 5_000L));
        client.environment().limitViews(islandId)
            .thenAccept(views -> cache.put(islandId, new CachedLimits(limitValues(views), System.currentTimeMillis() + TTL_MILLIS)))
            .exceptionally(exception -> {
                cache.put(islandId, new CachedLimits(fallbackValues, System.currentTimeMillis() + TTL_MILLIS));
                return null;
            });
        return cached == null ? fallback : cached.value(limitKey, fallback);
    }

    public void invalidate(UUID islandId) {
        cache.remove(islandId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private Map<String, Long> limitValues(java.util.List<CoreGuiViews.LimitView> views) {
        if (views == null || views.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> values = new ConcurrentHashMap<>();
        for (CoreGuiViews.LimitView view : views) {
            if (view != null && !view.key().isBlank()) {
                values.put(view.key().toUpperCase(), view.value());
            }
        }
        return Map.copyOf(values);
    }

    private record CachedLimits(Map<String, Long> values, long expiresAtMillis) {
        long value(String key, long fallback) {
            return values.getOrDefault(key.toUpperCase(), fallback);
        }
    }
}
