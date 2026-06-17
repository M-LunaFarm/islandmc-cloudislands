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
        assertTrue(keys.contains("ci:island:" + ISLAND + ":summary"));
    }
}
