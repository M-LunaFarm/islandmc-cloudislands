package kr.lunaf.cloudislands.paper.cache;

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class LocalCacheManager {
    private final Map<String, CacheRegistration> caches = new ConcurrentHashMap<>();
    private final AtomicLong invalidations = new AtomicLong();

    public void register(String name, Runnable invalidateAll) {
        registerStats(name, invalidateAll, () -> 0L, () -> 1.0D);
    }

    public void registerStats(String name, Runnable invalidateAll, LongSupplier lookups, DoubleSupplier hitRatio) {
        String key = normalize(name);
        if (key.isBlank()) {
            return;
        }
        caches.put(key, new CacheRegistration(key, invalidateAll, lookups, hitRatio));
    }

    public int cacheCount() {
        return caches.size();
    }

    public long invalidationsTotal() {
        return invalidations.get();
    }

    public void invalidateAll() {
        for (CacheRegistration cache : caches.values()) {
            cache.invalidateAll().run();
        }
        invalidations.incrementAndGet();
    }

    public String namesCsv() {
        StringJoiner joiner = new StringJoiner(",");
        caches.keySet().stream().sorted().forEach(joiner::add);
        return joiner.toString();
    }

    public String prometheus(String nodeId) {
        StringBuilder out = new StringBuilder();
        out.append("cloudislands_paper_local_caches{node=\"").append(escapeLabel(nodeId)).append("\"} ").append(cacheCount()).append('\n');
        out.append("cloudislands_paper_local_cache_invalidations_total{node=\"").append(escapeLabel(nodeId)).append("\"} ").append(invalidationsTotal()).append('\n');
        caches.values().stream()
            .sorted((left, right) -> left.name().compareTo(right.name()))
            .forEach(cache -> {
                out.append("cloudislands_paper_local_cache_lookups_total{node=\"").append(escapeLabel(nodeId)).append("\",cache=\"").append(escapeLabel(cache.name())).append("\"} ").append(cache.lookups().getAsLong()).append('\n');
                out.append("cloudislands_paper_local_cache_hit_ratio{node=\"").append(escapeLabel(nodeId)).append("\",cache=\"").append(escapeLabel(cache.name())).append("\"} ").append(cache.hitRatio().getAsDouble()).append('\n');
            });
        return out.toString();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private static String escapeLabel(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record CacheRegistration(String name, Runnable invalidateAll, LongSupplier lookups, DoubleSupplier hitRatio) {}
}
