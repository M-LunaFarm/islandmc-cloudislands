package kr.lunaf.cloudislands.paper.placeholder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

final class BoundedStaleCache<K, V> {
    private final int maxEntries;
    private final long staleRetentionMillis;
    private final long maintenanceIntervalMillis;
    private final ToLongFunction<V> expiresAt;
    private final ConcurrentHashMap<K, V> entries = new ConcurrentHashMap<>();
    private final AtomicLong lastMaintenanceAt = new AtomicLong();

    BoundedStaleCache(int maxEntries, long staleRetentionMillis, long maintenanceIntervalMillis, ToLongFunction<V> expiresAt) {
        if (maxEntries <= 0 || staleRetentionMillis < 0L || maintenanceIntervalMillis < 0L) {
            throw new IllegalArgumentException("Cache bounds and durations must be non-negative, with a positive maximum size");
        }
        this.maxEntries = maxEntries;
        this.staleRetentionMillis = staleRetentionMillis;
        this.maintenanceIntervalMillis = maintenanceIntervalMillis;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    V get(K key) {
        return key == null ? null : entries.get(key);
    }

    void put(K key, V value, long nowMillis) {
        entries.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        maintain(nowMillis);
    }

    int size() {
        return entries.size();
    }

    void maintain(long nowMillis) {
        long previous = lastMaintenanceAt.get();
        boolean overCapacity = entries.size() > maxEntries;
        if (!overCapacity && previous > 0L && nowMillis - previous < maintenanceIntervalMillis) {
            return;
        }
        if (!lastMaintenanceAt.compareAndSet(previous, nowMillis) && !overCapacity) {
            return;
        }

        long staleCutoff = nowMillis - staleRetentionMillis;
        entries.entrySet().removeIf(entry -> expiresAt.applyAsLong(entry.getValue()) < staleCutoff);
        int excess = entries.size() - maxEntries;
        if (excess <= 0) {
            return;
        }
        ArrayList<Map.Entry<K, V>> oldest = new ArrayList<>(entries.entrySet());
        oldest.sort(Comparator.comparingLong(entry -> expiresAt.applyAsLong(entry.getValue())));
        for (int index = 0; index < excess && index < oldest.size(); index++) {
            Map.Entry<K, V> entry = oldest.get(index);
            entries.remove(entry.getKey(), entry.getValue());
        }
    }
}
