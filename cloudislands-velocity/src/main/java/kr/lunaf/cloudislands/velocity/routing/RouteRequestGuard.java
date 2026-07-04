package kr.lunaf.cloudislands.velocity.routing;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteRequestGuard {
    private final Map<RequestKey, Long> recentRequests = new ConcurrentHashMap<>();
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
        return allow(playerUuid, "route", false);
    }

    public boolean allow(UUID playerUuid, String action) {
        return allow(playerUuid, action, false);
    }

    public boolean allow(UUID playerUuid, String action, boolean bypass) {
        if (bypass) {
            return true;
        }
        RequestKey key = new RequestKey(playerUuid, action);
        long now = clock.millis();
        Long previous = recentRequests.put(key, now);
        if (previous == null || now - previous >= cooldownMillis) {
            return true;
        }
        recentRequests.put(key, previous);
        return false;
    }

    public void clear(UUID playerUuid) {
        if (playerUuid != null) {
            recentRequests.keySet().removeIf(key -> key.playerUuid().equals(playerUuid));
        }
    }

    private record RequestKey(UUID playerUuid, String action) {
        RequestKey {
            action = action == null || action.isBlank() ? "route" : action.toLowerCase(java.util.Locale.ROOT);
        }
    }
}
