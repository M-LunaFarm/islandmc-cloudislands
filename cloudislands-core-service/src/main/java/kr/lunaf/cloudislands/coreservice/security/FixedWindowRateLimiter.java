package kr.lunaf.cloudislands.coreservice.security;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FixedWindowRateLimiter {
    private final Clock clock;
    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(Clock clock, int maxRequests, long windowMillis) {
        this.clock = clock;
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String key) {
        long now = clock.millis();
        Bucket bucket = buckets.compute(key, (ignored, current) -> current == null || now >= current.windowStart() + windowMillis ? new Bucket(now, 1) : current.increment());
        return bucket.count() <= maxRequests;
    }

    private record Bucket(long windowStart, int count) {
        private Bucket increment() {
            return new Bucket(windowStart, count + 1);
        }
    }
}
