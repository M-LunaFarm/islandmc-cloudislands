package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandLimitCache {
    private static final long TTL_MILLIS = 30_000L;
    private static final Pattern LIMIT_OBJECT = Pattern.compile("\\{[^{}]*\"limitKey\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"value\"\\s*:\\s*(\\d+)[^{}]*}");
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
        client.listIslandLimits(islandId)
            .thenAccept(body -> cache.put(islandId, new CachedLimits(parse(body), System.currentTimeMillis() + TTL_MILLIS)))
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

    private Map<String, Long> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Long> values = new ConcurrentHashMap<>();
        Matcher matcher = LIMIT_OBJECT.matcher(json);
        while (matcher.find()) {
            try {
                values.put(matcher.group(1).toUpperCase(), Long.parseLong(matcher.group(2)));
            } catch (NumberFormatException ignored) {
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
