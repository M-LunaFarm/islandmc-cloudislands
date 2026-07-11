package kr.lunaf.cloudislands.paper.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MissionDefinitionCacheTest {
    @Test
    void coalescesRequestsUntilTtlExpiresAndSupportsInvalidation() {
        AtomicLong now = new AtomicLong(1000L);
        AtomicInteger loads = new AtomicInteger();
        MissionDefinitionCache cache = new MissionDefinitionCache(8, Duration.ofSeconds(2), now::get);
        UUID islandId = UUID.randomUUID();

        CompletableFuture<MissionDefinitionCache.DefinitionViews> first = cache.get(islandId, () -> loaded(loads));
        CompletableFuture<MissionDefinitionCache.DefinitionViews> cached = cache.get(islandId, () -> loaded(loads));
        assertSame(first, cached);
        assertEquals(1, loads.get());

        now.addAndGet(2001L);
        cache.get(islandId, () -> loaded(loads));
        assertEquals(2, loads.get());

        cache.invalidate(islandId);
        cache.get(islandId, () -> loaded(loads));
        assertEquals(3, loads.get());
    }

    @Test
    void failedLoadsAreRemovedAndCapacityIsBounded() {
        AtomicLong now = new AtomicLong(1000L);
        MissionDefinitionCache cache = new MissionDefinitionCache(2, Duration.ofSeconds(2), now::get);
        UUID failedIsland = UUID.randomUUID();

        cache.get(failedIsland, () -> CompletableFuture.failedFuture(new IllegalStateException("core unavailable")));
        assertEquals(0, cache.size());

        cache.get(UUID.randomUUID(), MissionDefinitionCacheTest::empty);
        now.incrementAndGet();
        cache.get(UUID.randomUUID(), MissionDefinitionCacheTest::empty);
        now.incrementAndGet();
        cache.get(UUID.randomUUID(), MissionDefinitionCacheTest::empty);
        assertEquals(2, cache.size());
    }

    private static CompletableFuture<MissionDefinitionCache.DefinitionViews> loaded(AtomicInteger loads) {
        loads.incrementAndGet();
        return empty();
    }

    private static CompletableFuture<MissionDefinitionCache.DefinitionViews> empty() {
        return CompletableFuture.completedFuture(new MissionDefinitionCache.DefinitionViews(List.of(), List.of()));
    }
}
