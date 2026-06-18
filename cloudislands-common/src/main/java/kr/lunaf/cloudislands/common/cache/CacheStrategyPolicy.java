package kr.lunaf.cloudislands.common.cache;

import java.util.Set;

public final class CacheStrategyPolicy {
    public static final String CACHE_LAYER_ORDER = "L1-paper-velocity-local-memory>L2-redis>L3-postgresql";
    public static final String SOURCE_OF_TRUTH = "postgresql";
    public static final String REDIS_ROLE = "cache-heartbeat-lock-stream-and-queue-helper-not-authoritative-storage";
    public static final String INVALIDATION_FANOUT = "core-global-event>paper-local-cache-delete>velocity-route-cache-delete";
    public static final String WRITE_INVALIDATION_POLICY = "core-api-publishes-global-event-after-successful-write";

    public static final Set<String> LOCAL_CACHE_TARGETS = Set.of(
        "player-island",
        "island-summary",
        "island-runtime",
        "island-members",
        "island-permissions",
        "island-flags",
        "island-warps",
        "node-heartbeat"
    );

    private CacheStrategyPolicy() {
    }

    public static boolean localCacheTarget(String target) {
        return target != null && LOCAL_CACHE_TARGETS.contains(target);
    }
}
