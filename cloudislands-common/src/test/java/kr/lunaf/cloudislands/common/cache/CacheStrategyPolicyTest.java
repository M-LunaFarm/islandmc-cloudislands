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
