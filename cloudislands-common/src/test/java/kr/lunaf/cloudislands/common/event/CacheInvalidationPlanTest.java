package kr.lunaf.cloudislands.common.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheInvalidationPlanTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000501");

    @Test
    void islandCreationClearsRuntimeMembershipPermissionAndRouteCaches() {
        Set<CacheInvalidationPlan.CacheTarget> targets = CacheInvalidationPlan.targetsFor(CloudIslandEventType.ISLAND_CREATED);

        assertEquals(
                Set.of(
                        CacheInvalidationPlan.CacheTarget.PLAYER_ISLAND,
                        CacheInvalidationPlan.CacheTarget.RUNTIME,
                        CacheInvalidationPlan.CacheTarget.MEMBERS,
                        CacheInvalidationPlan.CacheTarget.PERMISSIONS,
                        CacheInvalidationPlan.CacheTarget.ROUTE,
                        CacheInvalidationPlan.CacheTarget.SUMMARY),
                targets);
    }

    @Test
    void visitFlowClearsRouteTicketsAcrossIslandNodes() {
        Set<String> keys = CacheInvalidationPlan.redisKeysFor(CloudIslandEventType.ISLAND_VISITOR_KICKED, ISLAND);

        assertTrue(keys.contains("ci:island:" + ISLAND + ":route-tickets"));
        assertTrue(keys.contains("ci:island:" + ISLAND + ":summary"));
    }

    @Test
    void failedDeleteBackupKeepsSnapshotAndRuntimeStateFresh() {
        Set<String> keys = CacheInvalidationPlan.redisKeysFor(CloudIslandEventType.ISLAND_DELETE_BACKUP_FAILED, ISLAND);

        assertTrue(keys.contains("ci:island:" + ISLAND + ":runtime"));
        assertTrue(keys.contains("ci:island:" + ISLAND + ":snapshots"));
        assertTrue(keys.contains("ci:island:" + ISLAND + ":route-tickets"));
        assertTrue(keys.contains("ci:island:" + ISLAND + ":summary"));
    }

    @Test
    void playerIslandCacheInvalidatesOnlyWhenEventCarriesPlayerScope() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000601");

        Set<String> keys = CacheInvalidationPlan.playerRedisKeysFor(CloudIslandEventType.ISLAND_MEMBER_CHANGED, player);

        assertEquals(Set.of("ci:player:" + player + ":island"), keys);
        assertTrue(CacheInvalidationPlan.playerRedisKeysFor(CloudIslandEventType.ISLAND_RUNTIME_CHANGED, player).isEmpty());
        assertTrue(CacheInvalidationPlan.playerRedisKeysFor(CloudIslandEventType.ISLAND_MEMBER_CHANGED, null).isEmpty());
    }

    @Test
    void nodeStateChangeInvalidatesHeartbeatStateAndMetricsCaches() {
        Set<CacheInvalidationPlan.CacheTarget> targets = CacheInvalidationPlan.targetsFor(CloudIslandEventType.NODE_STATE_CHANGED);
        Set<String> keys = CacheInvalidationPlan.nodeRedisKeysFor(CloudIslandEventType.NODE_STATE_CHANGED, "island-2");

        assertTrue(targets.contains(CacheInvalidationPlan.CacheTarget.NODE_HEARTBEAT));
        assertTrue(targets.contains(CacheInvalidationPlan.CacheTarget.RUNTIME));
        assertEquals(Set.of(
                "ci:server:island-2:heartbeat",
                "ci:server:island-2:state",
                "ci:server:island-2:metrics"
        ), keys);
        assertTrue(CacheInvalidationPlan.nodeRedisKeysFor(CloudIslandEventType.ISLAND_MEMBER_CHANGED, "island-2").isEmpty());
    }

    @Test
    void reviewAndWarehouseEventsHaveExplicitSummaryInvalidationPlans() {
        assertEquals(Set.of(CacheInvalidationPlan.CacheTarget.SUMMARY), CacheInvalidationPlan.targetsFor(CloudIslandEventType.ISLAND_REVIEW_CHANGED));
        assertEquals(Set.of(CacheInvalidationPlan.CacheTarget.SUMMARY), CacheInvalidationPlan.targetsFor(CloudIslandEventType.ISLAND_WAREHOUSE_CHANGED));
    }
}
