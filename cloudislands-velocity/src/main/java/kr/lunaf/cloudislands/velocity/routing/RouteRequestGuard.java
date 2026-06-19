package kr.lunaf.cloudislands.velocity.routing;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteRequestGuard {
    private final Map<UUID, Long> recentRequests = new ConcurrentHashMap<>();
    private final long cooldownMillis;
    private final Clock clock;

    public RouteRequestGuard(long cooldownMillis) {
        this(cooldownMillis, Clock.systemUTC());
    }

    RouteRequestGuard(long cooldownMillis, Clock clock) {
        this.cooldownMillis = Math.max(0L, cooldownMillis);
        this.clock = clock;
    }

    public boolean allow(UUID playerUuid) {
        long now = clock.millis();
        Long previous = recentRequests.put(playerUuid, now);
        if (previous == null || now - previous >= cooldownMillis) {
            return true;
        }
        recentRequests.put(playerUuid, previous);
        return false;
    }

    public void clear(UUID playerUuid) {
        if (playerUuid != null) {
            recentRequests.remove(playerUuid);
        }
    }
}
