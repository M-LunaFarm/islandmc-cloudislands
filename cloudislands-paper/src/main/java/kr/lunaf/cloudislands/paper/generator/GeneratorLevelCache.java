package kr.lunaf.cloudislands.paper.generator;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

public final class GeneratorLevelCache {
    private static final long TTL_MILLIS = 30_000L;
    private final CoreApiClient client;
    private final String defaultGeneratorKey;
    private final Map<UUID, CachedSelection> cache = new ConcurrentHashMap<>();

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
        return selection(islandId).profile();
    }

    public GeneratorSelection selection(UUID islandId) {
        CachedSelection cached = cache.get(islandId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.selection();
        }
        GeneratorSelection fallback = cached == null
            ? new GeneratorSelection(new GeneratorProfile(defaultGeneratorKey, 1), List.of())
            : cached.selection();
        cache.put(islandId, new CachedSelection(fallback, now + 5_000L));
        client.generators().generator(islandId)
            .thenCompose(profile -> client.generators().generatorRules(islandId)
                .thenApply(rules -> new GeneratorSelection(profile(profile), rules == null ? List.of() : rules)))
            .thenAccept(selection -> cache.put(islandId, new CachedSelection(selection, System.currentTimeMillis() + TTL_MILLIS)))
            .exceptionally(exception -> {
                cache.put(islandId, new CachedSelection(fallback, System.currentTimeMillis() + TTL_MILLIS));
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

    private static GeneratorProfile profile(IslandGeneratorSnapshot snapshot) {
        return snapshot == null
            ? new GeneratorProfile("default", 1)
            : new GeneratorProfile(normalizeProfileKey(snapshot.generatorKey()), Math.max(1, snapshot.level()));
    }

    public record GeneratorProfile(String generatorKey, int level) {
        public GeneratorProfile {
            generatorKey = normalizeProfileKey(generatorKey);
            level = Math.max(1, level);
        }
    }

    public record GeneratorSelection(GeneratorProfile profile, List<GeneratorRuleSnapshot> rules) {
        public GeneratorSelection {
            profile = profile == null ? new GeneratorProfile("default", 1) : profile;
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    private record CachedSelection(GeneratorSelection selection, long expiresAtMillis) {}
}
