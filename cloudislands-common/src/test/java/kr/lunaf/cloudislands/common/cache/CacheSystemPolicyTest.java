package kr.lunaf.cloudislands.common.cache;

import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheSystemPolicyTest {
    @Test
    void pinsThreeLevelCacheStrategy() {
        assertEquals(CacheStrategyPolicy.CACHE_LAYER_ORDER, CacheSystemPolicy.THREE_LEVEL_CACHE_POLICY);
        assertEquals("postgresql", CacheStrategyPolicy.SOURCE_OF_TRUTH);
        assertEquals("cache-heartbeat-lock-stream-and-queue-helper-not-authoritative-storage", CacheStrategyPolicy.REDIS_ROLE);
    }

    @Test
    void pinsGoalCacheTargets() {
        assertEquals(
                List.of(
                        "player_uuid->island_id",
                        "island_id->summary",
                        "island_id->runtime",
                        "island_id->members",
                        "island_id->permissions",
                        "island_id->flags",
                        "island_id->warps",
                        "node_id->heartbeat"
                ),
                CacheSystemPolicy.cacheTargets()
        );
        assertTrue(CacheStrategyPolicy.localCacheTarget("player-island"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("island-summary"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("island-runtime"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("island-members"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("island-permissions"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("island-flags"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("island-warps"));
        assertTrue(CacheStrategyPolicy.localCacheTarget("node-heartbeat"));
    }

    @Test
    void pinsGoalInvalidationEventsToConcreteTargets() {
        assertTargets(CloudIslandEventType.ISLAND_MEMBER_CHANGED, CacheInvalidationPlan.CacheTarget.MEMBERS, CacheInvalidationPlan.CacheTarget.PERMISSIONS);
        assertTargets(CloudIslandEventType.ISLAND_FLAG_CHANGED, CacheInvalidationPlan.CacheTarget.FLAGS, CacheInvalidationPlan.CacheTarget.ROUTE);
        assertTargets(CloudIslandEventType.ISLAND_PERMISSION_CHANGED, CacheInvalidationPlan.CacheTarget.PERMISSIONS);
        assertTargets(CloudIslandEventType.ISLAND_WARP_CHANGED, CacheInvalidationPlan.CacheTarget.WARPS, CacheInvalidationPlan.CacheTarget.ROUTE);
        assertTargets(CloudIslandEventType.ISLAND_RUNTIME_CHANGED, CacheInvalidationPlan.CacheTarget.RUNTIME, CacheInvalidationPlan.CacheTarget.ROUTE);
        assertEquals(Set.copyOf(java.util.Arrays.asList(CacheInvalidationPlan.CacheTarget.values())), CacheInvalidationPlan.targetsFor(CloudIslandEventType.ISLAND_DELETED));
    }

    @Test
    void pinsFlagChangeFanoutAcrossIslandNodesLobbyAndVelocity() {
        assertEquals(List.of("local-cache-delete:flags:A"), CacheSystemPolicy.flagChangeFanoutExample().get("Island-1"));
        assertEquals(List.of("local-cache-delete:flags:A"), CacheSystemPolicy.flagChangeFanoutExample().get("Island-2"));
        assertEquals(List.of("local-cache-delete:flags:A"), CacheSystemPolicy.flagChangeFanoutExample().get("Lobby"));
        assertEquals(List.of("route-cache-delete-if-affected"), CacheSystemPolicy.flagChangeFanoutExample().get("Velocity"));
    }

    private void assertTargets(CloudIslandEventType eventType, CacheInvalidationPlan.CacheTarget... targets) {
        assertTrue(CacheSystemPolicy.goalInvalidationEvent(eventType), eventType.name());
        Set<CacheInvalidationPlan.CacheTarget> actual = CacheInvalidationPlan.targetsFor(eventType);
        for (CacheInvalidationPlan.CacheTarget target : targets) {
            assertTrue(actual.contains(target), eventType.name() + " -> " + target.name());
        }
    }
}
