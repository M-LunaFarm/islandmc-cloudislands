package kr.lunaf.cloudislands.paper.generator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class GeneratorLevelCache {
    private static final long TTL_MILLIS = 30_000L;
    private static final Pattern GENERATOR_UPGRADE = Pattern.compile("\\{[^{}]*\"upgradeKey\"\\s*:\\s*\"generator\"[^{}]*}", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL_FIELD = Pattern.compile("\"level\"\\s*:\\s*(\\d+)");
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
        int fallback = cached == null ? 1 : cached.level();
        cache.put(islandId, new CachedLevel(fallback, now + 5_000L));
        client.listIslandUpgrades(islandId)
            .thenAccept(body -> cache.put(islandId, new CachedLevel(parseGeneratorLevel(body), System.currentTimeMillis() + TTL_MILLIS)))
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

    private int parseGeneratorLevel(String json) {
        if (json == null || json.isBlank()) {
            return 1;
        }
        Matcher upgrade = GENERATOR_UPGRADE.matcher(json);
        if (!upgrade.find()) {
            return 1;
        }
        Matcher level = LEVEL_FIELD.matcher(upgrade.group());
        if (!level.find()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(level.group(1)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private record CachedLevel(int level, long expiresAtMillis) {}
}
