package kr.lunaf.cloudislands.paper.generator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class GeneratorLevelCache {
    private static final long TTL_MILLIS = 30_000L;
    private final CoreApiClient client;
    private final Map<UUID, CachedLevel> cache = new ConcurrentHashMap<>();

    public GeneratorLevelCache(CoreApiClient client) {
        this.client = client;
    }

    public int level(UUID islandId) {
        CachedLevel cached = cache.get(islandId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.level();
        }
        cache.put(islandId, new CachedLevel(cached == null ? 1 : cached.level(), now + 5_000L));
        client.listIslandUpgrades(islandId).thenAccept(body -> cache.put(islandId, new CachedLevel(parseGeneratorLevel(body), System.currentTimeMillis() + TTL_MILLIS)));
        return cached == null ? 1 : cached.level();
    }

    public void invalidate(UUID islandId) {
        cache.remove(islandId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private int parseGeneratorLevel(String json) {
        if (json == null || json.isBlank()) {
            return 1;
        }
        int marker = json.indexOf("\"upgradeKey\":\"generator\"");
        if (marker < 0) {
            return 1;
        }
        int levelMarker = json.indexOf("\"level\":", marker);
        if (levelMarker < 0) {
            return 1;
        }
        int start = levelMarker + "\"level\":".length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(json.substring(start, end)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private record CachedLevel(int level, long expiresAtMillis) {}
}
