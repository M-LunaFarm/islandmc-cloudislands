package kr.lunaf.cloudislands.common.cache;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheStrategyPolicyTest {
    @Test
    void keepsCacheLayerOrderAndAuthorityClear() {
        assertEquals("L1-paper-velocity-local-memory>L2-redis>L3-postgresql", CacheStrategyPolicy.CACHE_LAYER_ORDER);
        assertEquals("postgresql", CacheStrategyPolicy.SOURCE_OF_TRUTH);
        assertEquals("cache-heartbeat-lock-stream-and-queue-helper-not-authoritative-storage", CacheStrategyPolicy.REDIS_ROLE);
        assertEquals("heartbeat,state,metrics,player-island,route-ticket,session,island-summary,runtime,members,flags,permissions,warps,locks,streams", CacheStrategyPolicy.REDIS_KEY_SCOPE);
        assertEquals("heartbeat=5s,route-ticket=30s,player-island=5m,island-summary=1m,permissions=30s,lock=10s-60s", CacheStrategyPolicy.REDIS_TTL_SUMMARY);
        assertEquals("redis-locks-are-advisory-postgresql-row-lock-and-fencing-token-authorize-final-writes", CacheStrategyPolicy.CONSISTENCY_GUARD);
    }

    @Test
    void keepsWriteInvalidationFanoutStable() {
        assertEquals("core-api-publishes-global-event-after-successful-write", CacheStrategyPolicy.WRITE_INVALIDATION_POLICY);
        assertEquals("core-global-event>paper-local-cache-delete>velocity-route-cache-delete", CacheStrategyPolicy.INVALIDATION_FANOUT);
    }

    @Test
    void listsLocalCacheTargetsFromGoal() {
        assertEquals(Set.of(
            "player-island",
            "island-summary",
            "island-runtime",
            "island-members",
            "island-permissions",
            "island-flags",
            "island-warps",
            "node-heartbeat"
        ), CacheStrategyPolicy.LOCAL_CACHE_TARGETS);
        assertTrue(CacheStrategyPolicy.localCacheTarget("island-runtime"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("node-heartbeat"));
        assertFalse(CacheStrategyPolicy.localCacheTarget("postgresql-row"));
        assertFalse(CacheStrategyPolicy.localCacheTarget(null));
    }
}
