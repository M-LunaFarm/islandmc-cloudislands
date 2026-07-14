package kr.lunaf.cloudislands.paper.platform.world;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

final class WorldFlushReusePolicy {
    private final long reuseMillis;
    private final Map<String, Long> lastFlushMillis = new HashMap<>();

    WorldFlushReusePolicy(Duration reuse) {
        this.reuseMillis = Math.max(0L, reuse == null ? 0L : reuse.toMillis());
    }

    synchronized boolean requiresFlush(String worldName, String reason, long nowMillis) {
        if (reason != null && !reason.isBlank() && !"AUTO".equalsIgnoreCase(reason)) {
            return true;
        }
        Long previous = lastFlushMillis.get(worldName);
        return previous == null || nowMillis < previous || nowMillis - previous >= reuseMillis;
    }

    synchronized void record(String worldName, long nowMillis) {
        lastFlushMillis.put(worldName, nowMillis);
    }
}
