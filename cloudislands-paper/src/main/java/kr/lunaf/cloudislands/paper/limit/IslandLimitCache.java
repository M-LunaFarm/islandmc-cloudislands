package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;

public final class IslandLimitCache {
    private static final long TTL_MILLIS = 30_000L;
    private final Function<UUID, java.util.concurrent.CompletableFuture<java.util.List<CoreGuiViews.LimitView>>> limitLoader;
    private final Function<UUID, java.util.concurrent.CompletableFuture<Map<String, Long>>> blockCountLoader;
    private final Map<UUID, CachedLimits> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedBlockCounts> blockCounts = new ConcurrentHashMap<>();
    private final AtomicLong refreshSequence = new AtomicLong();

    public IslandLimitCache(CoreApiClient client) {
        this(client.environment()::limitViews, client.progression()::blockCounts);
    }

    IslandLimitCache(
        Function<UUID, java.util.concurrent.CompletableFuture<java.util.List<CoreGuiViews.LimitView>>> limitLoader,
        Function<UUID, java.util.concurrent.CompletableFuture<Map<String, Long>>> blockCountLoader
    ) {
        this.limitLoader = limitLoader;
        this.blockCountLoader = blockCountLoader;
    }

    public long limit(UUID islandId, String limitKey, long fallback) {
        OptionalLong lookup = limitIfReady(islandId, limitKey, fallback);
        return lookup.orElse(fallback);
    }

    public OptionalLong limitIfReady(UUID islandId, String limitKey, long fallback) {
        CachedLimits cached = cache.get(islandId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.loaded() ? OptionalLong.of(cached.value(limitKey, fallback)) : OptionalLong.empty();
        }
        Map<String, Long> fallbackValues = cached == null ? Map.of() : cached.values();
        boolean fallbackLoaded = cached != null && cached.loaded();
        long refreshId = refreshSequence.incrementAndGet();
        cache.put(islandId, new CachedLimits(fallbackValues, fallbackLoaded, now + 5_000L, refreshId));
        limitLoader.apply(islandId)
            .thenAccept(views -> completeRefresh(islandId, refreshId, limitValues(views)))
            .exceptionally(exception -> {
                completeRefresh(islandId, refreshId, fallbackValues, fallbackLoaded);
                return null;
            });
        return fallbackLoaded ? OptionalLong.of(cached.value(limitKey, fallback)) : OptionalLong.empty();
    }

    public OptionalLong blockCountIfReady(UUID islandId, String limitKey) {
        CachedBlockCounts cached = blockCounts.get(islandId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.loaded()) {
            return OptionalLong.of(cached.value(limitKey));
        }
        if (cached == null || (!cached.loading() && cached.retryAtMillis() <= now)) {
            long refreshId = refreshSequence.incrementAndGet();
            CachedBlockCounts loading = new CachedBlockCounts(Map.of(), false, true, now + 2_000L, refreshId);
            blockCounts.put(islandId, loading);
            blockCountLoader.apply(islandId)
                .thenAccept(counts -> completeBlockCountRefresh(islandId, refreshId, counts))
                .exceptionally(exception -> {
                    failBlockCountRefresh(islandId, refreshId);
                    return null;
                });
        }
        return OptionalLong.empty();
    }

    public void recordBlockDelta(UUID islandId, String materialKey, long delta) {
        if (islandId == null || materialKey == null || materialKey.isBlank() || delta == 0L) {
            return;
        }
        blockCounts.computeIfPresent(islandId, (ignored, current) -> {
            if (!current.loaded()) {
                return current;
            }
            Map<String, Long> updated = new java.util.HashMap<>(current.values());
            if (materialKey.startsWith(IslandBlockLimitKeys.COUNT_PREFIX) && !updated.containsKey(materialKey)) {
                String limitKey = materialKey.substring(IslandBlockLimitKeys.COUNT_PREFIX.length());
                updated.put(materialKey, Math.max(0L, current.value(limitKey) + delta));
            } else {
                updated.merge(materialKey, delta, (left, right) -> Math.max(0L, left + right));
            }
            updated.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0L);
            return new CachedBlockCounts(Map.copyOf(updated), true, false, 0L, current.refreshId());
        });
    }

    public void replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        if (islandId == null) {
            return;
        }
        blockCounts.put(islandId, new CachedBlockCounts(positiveCounts(counts), true, false, 0L, refreshSequence.incrementAndGet()));
    }

    public void invalidate(UUID islandId) {
        cache.remove(islandId);
        blockCounts.remove(islandId);
    }

    public void invalidateAll() {
        cache.clear();
        blockCounts.clear();
    }

    private void completeRefresh(UUID islandId, long refreshId, Map<String, Long> values) {
        completeRefresh(islandId, refreshId, values, true);
    }

    private void completeRefresh(UUID islandId, long refreshId, Map<String, Long> values, boolean loaded) {
        cache.computeIfPresent(islandId, (ignored, current) -> current.refreshId() == refreshId
            ? new CachedLimits(values, loaded, System.currentTimeMillis() + (loaded ? TTL_MILLIS : 2_000L), refreshId)
            : current);
    }

    private void completeBlockCountRefresh(UUID islandId, long refreshId, Map<String, Long> values) {
        blockCounts.computeIfPresent(islandId, (ignored, current) -> current.refreshId() == refreshId
            ? new CachedBlockCounts(positiveCounts(values), true, false, 0L, refreshId)
            : current);
    }

    private void failBlockCountRefresh(UUID islandId, long refreshId) {
        blockCounts.computeIfPresent(islandId, (ignored, current) -> current.refreshId() == refreshId
            ? new CachedBlockCounts(Map.of(), false, false, System.currentTimeMillis() + 2_000L, refreshId)
            : current);
    }

    private Map<String, Long> positiveCounts(Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> positive = new java.util.HashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && value > 0L) {
                positive.put(key, value);
            }
        });
        return Map.copyOf(positive);
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

    private record CachedLimits(Map<String, Long> values, boolean loaded, long expiresAtMillis, long refreshId) {
        long value(String key, long fallback) {
            return values.getOrDefault(key.toUpperCase(), fallback);
        }
    }

    private record CachedBlockCounts(Map<String, Long> values, boolean loaded, boolean loading, long retryAtMillis, long refreshId) {
        long value(String limitKey) {
            String normalized = limitKey == null ? "" : limitKey.trim().toUpperCase();
            if (GameplayParityPolicy.blockAmountLimit(normalized)) {
                String materialKey = GameplayParityPolicy.blockAmountMaterialKey(normalized).toLowerCase(java.util.Locale.ROOT);
                return values.getOrDefault(materialKey, 0L);
            }
            if (GameplayParityPolicy.entityTypeLimit(normalized)) {
                String entityKey = GameplayParityPolicy.entityTypeLimitEntityKey(normalized).toLowerCase(java.util.Locale.ROOT);
                return values.getOrDefault("entity:" + entityKey, 0L);
            }
            Long authoritative = values.get(IslandBlockLimitKeys.COUNT_PREFIX + normalized.toLowerCase(java.util.Locale.ROOT));
            if (authoritative != null) {
                return authoritative;
            }
            return values.entrySet().stream()
                .filter(entry -> matches(normalized, entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        }

        private static boolean matches(String limitKey, String materialKey) {
            String key = materialKey == null ? "" : materialKey.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (limitKey) {
                case "HOPPER" -> key.equals("hopper") || key.equals("minecraft:hopper");
                case "SPAWNER" -> key.equals("spawner") || key.equals("minecraft:spawner");
                case "REDSTONE" -> isRedstoneKey(key);
                case "ENTITY" -> key.startsWith("entity:");
                default -> false;
            };
        }

        private static boolean isRedstoneKey(String key) {
            int namespace = key.indexOf(':');
            String material = namespace >= 0 ? key.substring(namespace + 1) : key;
            return material.contains("redstone")
                || material.endsWith("_button")
                || material.endsWith("_pressure_plate")
                || material.endsWith("_piston")
                || material.endsWith("_rail")
                || java.util.Set.of(
                    "repeater", "comparator", "lever", "observer", "dispenser", "dropper",
                    "daylight_detector", "tripwire_hook", "trapped_chest", "target", "note_block"
                ).contains(material);
        }
    }
}
