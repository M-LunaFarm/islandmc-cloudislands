package kr.lunaf.cloudislands.paper.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BoundedStaleCacheTest {
    @Test
    void retainsRecentStaleValuesButRemovesValuesPastGracePeriod() {
        BoundedStaleCache<String, Value> cache = cache(10, 500L);
        cache.put("recent", new Value("recent", 900L), 1_000L);
        cache.put("old", new Value("old", 400L), 1_000L);

        assertNotNull(cache.get("recent"));
        assertNull(cache.get("old"));
    }

    @Test
    void evictsOldestEntriesWhenMaximumSizeIsExceeded() {
        BoundedStaleCache<String, Value> cache = cache(3, 10_000L);
        cache.put("one", new Value("one", 1_001L), 1_000L);
        cache.put("two", new Value("two", 1_002L), 1_000L);
        cache.put("three", new Value("three", 1_003L), 1_000L);
        cache.put("four", new Value("four", 1_004L), 1_000L);

        assertEquals(3, cache.size());
        assertNull(cache.get("one"));
        assertNotNull(cache.get("four"));
    }

    @Test
    void concurrentWritersRemainBounded() throws Exception {
        BoundedStaleCache<UUID, Value> cache = new BoundedStaleCache<>(100, 10_000L, 0L, Value::expiresAtMillis);
        int writers = 8;
        int writesPerThread = 250;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(writers)) {
            for (int writer = 0; writer < writers; writer++) {
                executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < writesPerThread; index++) {
                        cache.put(UUID.randomUUID(), new Value("value", 20_000L + index), 10_000L + index);
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        cache.maintain(20_000L);

        assertTrue(cache.size() <= 100, "cache must remain within its configured maximum after concurrent maintenance");
    }

    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedStaleCache<>(0, 1L, 1L, Value::expiresAtMillis));
        assertThrows(IllegalArgumentException.class, () -> new BoundedStaleCache<>(1, -1L, 1L, Value::expiresAtMillis));
    }

    private BoundedStaleCache<String, Value> cache(int maxEntries, long staleRetentionMillis) {
        return new BoundedStaleCache<>(maxEntries, staleRetentionMillis, 0L, Value::expiresAtMillis);
    }

    private record Value(String value, long expiresAtMillis) {
    }
}
