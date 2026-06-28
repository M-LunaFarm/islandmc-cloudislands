package kr.lunaf.cloudislands.paper.generator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

public final class GeneratorLevelCache {
    private static final long TTL_MILLIS = 30_000L;
    private final CoreApiClient client;
    private final String defaultGeneratorKey;
    private final Map<UUID, CachedLevel> cache = new ConcurrentHashMap<>();

    public GeneratorLevelCache(CoreApiClient client) {
        this(client, "default");
    }

    public GeneratorLevelCache(CoreApiClient client, String defaultGeneratorKey) {
        this.client = client;
        this.defaultGeneratorKey = normalizeKey(defaultGeneratorKey);
    }

    public int level(UUID islandId) {
        return profile(islandId).level();
    }

    public GeneratorProfile profile(UUID islandId) {
        CachedLevel cached = cache.get(islandId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.profile();
        }
        GeneratorProfile fallback = cached == null ? new GeneratorProfile(defaultGeneratorKey, 1) : cached.profile();
        cache.put(islandId, new CachedLevel(fallback, now + 5_000L));
        client.progression().upgrades(islandId)
            .thenAccept(upgrades -> cache.put(islandId, new CachedLevel(resolveProfile(upgrades, defaultGeneratorKey), System.currentTimeMillis() + TTL_MILLIS)))
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

    public long ttlSeconds() {
        return TTL_MILLIS / 1000L;
    }

    public static GeneratorProfile resolveProfile(java.util.List<CoreGuiViews.UpgradeView> upgrades, String defaultGeneratorKey) {
        String fallbackGeneratorKey = normalizeProfileKey(defaultGeneratorKey);
        if (upgrades == null || upgrades.isEmpty()) {
            return new GeneratorProfile(fallbackGeneratorKey, 1);
        }
        GeneratorProfile selected = new GeneratorProfile(fallbackGeneratorKey, 1);
        for (CoreGuiViews.UpgradeView upgrade : upgrades) {
            if (upgrade == null) {
                continue;
            }
            String upgradeKey = normalizeProfileKey(upgrade.key());
            if (!isGeneratorUpgrade(upgradeKey)) {
                continue;
            }
            int level = Math.max(1, upgrade.level());
            String generatorKey = normalizeProfileKey(upgrade.generatorKey());
            if (generatorKey.equals("default")) {
                generatorKey = generatorKey(upgradeKey, fallbackGeneratorKey);
            }
            if (level > selected.level() || (level == selected.level() && selected.generatorKey().equals(fallbackGeneratorKey) && !generatorKey.equals(fallbackGeneratorKey))) {
                selected = new GeneratorProfile(generatorKey, level);
            }
        }
        return selected;
    }

    private static boolean isGeneratorUpgrade(String upgradeKey) {
        return upgradeKey.equalsIgnoreCase("generator") || upgradeKey.toLowerCase(java.util.Locale.ROOT).startsWith("generator:");
    }

    private static String generatorKey(String upgradeKey, String fallbackGeneratorKey) {
        int separator = upgradeKey.indexOf(':');
        return separator < 0 ? fallbackGeneratorKey : normalizeProfileKey(upgradeKey.substring(separator + 1));
    }

    private String normalizeKey(String value) {
        return normalizeProfileKey(value);
    }

    private static String normalizeProfileKey(String value) {
        return value == null || value.isBlank() ? "default" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record GeneratorProfile(String generatorKey, int level) {}

    private record CachedLevel(GeneratorProfile profile, long expiresAtMillis) {}
}
