package kr.lunaf.cloudislands.coreservice.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FixedWindowRateLimiter {
    private final Clock clock;
    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastCleanupMillis;

    public FixedWindowRateLimiter(Clock clock, int maxRequests, long windowMillis) {
        this.clock = clock;
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String key) {
        if (maxRequests <= 0 || windowMillis <= 0L) {
            return true;
        }
        long now = clock.millis();
        cleanupExpiredBuckets(now);
        String safeKey = key == null || key.isBlank() ? "unknown" : key;
        Bucket bucket = buckets.compute(safeKey, (ignored, current) -> current == null || now >= current.windowStart() + windowMillis ? new Bucket(now, 1) : current.increment());
        return bucket.count() <= maxRequests;
    }

    private void cleanupExpiredBuckets(long now) {
        long cleanupAfter = lastCleanupMillis + windowMillis;
        if (now < cleanupAfter) {
            return;
        }
        lastCleanupMillis = now;
        buckets.entrySet().removeIf(entry -> now >= entry.getValue().windowStart() + windowMillis);
    }

    private record Bucket(long windowStart, int count) {
        private Bucket increment() {
            return new Bucket(windowStart, count + 1);
        }
    }
}
