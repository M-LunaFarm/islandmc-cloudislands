package kr.lunaf.cloudislands.paper.mission;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

final class MissionDefinitionCache {
    private final int maxEntries;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    MissionDefinitionCache(int maxEntries, Duration ttl) {
        this(maxEntries, ttl, System::currentTimeMillis);
    }

    MissionDefinitionCache(int maxEntries, Duration ttl, LongSupplier clock) {
        if (maxEntries <= 0 || ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Mission definition cache requires positive bounds and ttl");
        }
        this.maxEntries = maxEntries;
        this.ttlMillis = ttl.toMillis();
        this.clock = clock;
    }

    CompletableFuture<DefinitionViews> get(UUID islandId, Supplier<CompletableFuture<DefinitionViews>> loader) {
        long now = clock.getAsLong();
        Entry entry = entries.compute(islandId, (key, current) -> {
            if (current != null && current.expiresAtMillis() > now) {
                return current;
            }
            CompletableFuture<DefinitionViews> loaded = loader.get();
            return new Entry(loaded, now + ttlMillis);
        });
        entry.value().whenComplete((ignored, error) -> {
            if (error != null) {
                entries.remove(islandId, entry);
            }
        });
        evictOverflow();
        return entry.value();
    }

    void invalidate(UUID islandId) {
        if (islandId != null) {
            entries.remove(islandId);
        }
    }

    int size() {
        return entries.size();
    }

    private void evictOverflow() {
        int excess = entries.size() - maxEntries;
        if (excess <= 0) {
            return;
        }
        entries.entrySet().stream()
            .sorted(Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()))
            .limit(excess)
            .forEach(entry -> entries.remove(entry.getKey(), entry.getValue()));
    }

    record DefinitionViews(List<CoreGuiViews.MissionView> missions, List<CoreGuiViews.MissionView> challenges) {
        DefinitionViews {
            missions = missions == null ? List.of() : List.copyOf(missions);
            challenges = challenges == null ? List.of() : List.copyOf(challenges);
        }
    }

    private record Entry(CompletableFuture<DefinitionViews> value, long expiresAtMillis) {}
}
