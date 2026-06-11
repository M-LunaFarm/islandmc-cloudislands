package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

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
        cache.put(islandId, cached == null ? new CachedLimits(Map.of(), now + 5_000L) : new CachedLimits(cached.values(), now + 5_000L));
        client.listIslandLimits(islandId).thenAccept(body -> cache.put(islandId, new CachedLimits(parse(body), System.currentTimeMillis() + TTL_MILLIS)));
        return cached == null ? fallback : cached.value(limitKey, fallback);
    }

    public void invalidate(UUID islandId) {
        cache.remove(islandId);
    }

    private Map<String, Long> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Long> values = new ConcurrentHashMap<>();
        int index = 0;
        while (index >= 0 && index < json.length()) {
            int keyMarker = json.indexOf("\"limitKey\":\"", index);
            if (keyMarker < 0) {
                break;
            }
            int keyStart = keyMarker + "\"limitKey\":\"".length();
            int keyEnd = json.indexOf('"', keyStart);
            int valueMarker = json.indexOf("\"value\":", keyEnd);
            if (keyEnd < 0 || valueMarker < 0) {
                break;
            }
            int valueStart = valueMarker + "\"value\":".length();
            int valueEnd = valueStart;
            while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
                valueEnd++;
            }
            if (valueEnd > valueStart) {
                try {
                    values.put(json.substring(keyStart, keyEnd).toUpperCase(), Long.parseLong(json.substring(valueStart, valueEnd)));
                } catch (NumberFormatException ignored) {
                }
            }
            index = valueEnd;
        }
        return Map.copyOf(values);
    }

    private record CachedLimits(Map<String, Long> values, long expiresAtMillis) {
        long value(String key, long fallback) {
            return values.getOrDefault(key.toUpperCase(), fallback);
        }
    }
}
