package kr.lunaf.cloudislands.paper.generator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class GeneratorLevelCache {
    private static final long TTL_MILLIS = 30_000L;
    private static final Pattern UPGRADE_OBJECT = Pattern.compile("\\{[^{}]*\"upgradeKey\"\\s*:\\s*\"[^\"]+\"[^{}]*}", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPGRADE_KEY_FIELD = Pattern.compile("\"upgradeKey\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATOR_KEY_FIELD = Pattern.compile("\"generatorKey\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL_FIELD = Pattern.compile("\"level\"\\s*:\\s*(\\d+)");
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
        client.listIslandUpgrades(islandId)
            .thenAccept(body -> cache.put(islandId, new CachedLevel(parseGeneratorProfile(body), System.currentTimeMillis() + TTL_MILLIS)))
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

    private GeneratorProfile parseGeneratorProfile(String json) {
        if (json == null || json.isBlank()) {
            return new GeneratorProfile(defaultGeneratorKey, 1);
        }
        GeneratorProfile selected = new GeneratorProfile(defaultGeneratorKey, 1);
        Matcher upgrade = UPGRADE_OBJECT.matcher(json);
        while (upgrade.find()) {
            String object = upgrade.group();
            String upgradeKey = text(object, UPGRADE_KEY_FIELD, "");
            if (!isGeneratorUpgrade(upgradeKey)) {
                continue;
            }
            int level = level(object);
            String generatorKey = text(object, GENERATOR_KEY_FIELD, generatorKey(upgradeKey));
            if (level > selected.level() || (level == selected.level() && selected.generatorKey().equals(defaultGeneratorKey) && !generatorKey.equals(defaultGeneratorKey))) {
                selected = new GeneratorProfile(generatorKey, level);
            }
        }
        return selected;
    }

    private int level(String object) {
        Matcher level = LEVEL_FIELD.matcher(object);
        if (!level.find()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(level.group(1)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean isGeneratorUpgrade(String upgradeKey) {
        return upgradeKey.equalsIgnoreCase("generator") || upgradeKey.toLowerCase(java.util.Locale.ROOT).startsWith("generator:");
    }

    private String generatorKey(String upgradeKey) {
        int separator = upgradeKey.indexOf(':');
        return separator < 0 ? defaultGeneratorKey : normalizeKey(upgradeKey.substring(separator + 1));
    }

    private String text(String object, Pattern pattern, String fallback) {
        Matcher matcher = pattern.matcher(object);
        return matcher.find() ? normalizeKey(matcher.group(1)) : fallback;
    }

    private String normalizeKey(String value) {
        return value == null || value.isBlank() ? "default" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record GeneratorProfile(String generatorKey, int level) {}

    private record CachedLevel(GeneratorProfile profile, long expiresAtMillis) {}
}
