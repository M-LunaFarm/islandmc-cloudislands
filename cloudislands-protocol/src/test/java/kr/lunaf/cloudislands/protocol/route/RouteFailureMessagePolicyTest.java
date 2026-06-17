package kr.lunaf.cloudislands.protocol.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFailureMessagePolicyTest {
    @Test
    void mapsVisitFailuresToPlayerSafeMessages() {
        assertEquals("해당 섬은 비공개 상태입니다.", RouteFailureMessagePolicy.playerMessage("ISLAND_PRIVATE", "fallback"));
        assertEquals("해당 섬에 방문할 수 없습니다.", RouteFailureMessagePolicy.playerMessage("VISITOR_BANNED", "fallback"));
        assertEquals("현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.", RouteFailureMessagePolicy.playerMessage("NODE_UNAVAILABLE", "fallback"));
        assertEquals("섬을 준비하지 못했습니다. 잠시 후 다시 시도해주세요.", RouteFailureMessagePolicy.playerMessage("ISLAND_LOADING_FAILED", "fallback"));
        assertEquals("대상 플레이어의 섬을 찾을 수 없습니다.", RouteFailureMessagePolicy.playerMessage("TARGET_OFFLINE_NO_ISLAND", "fallback"));
    }

    @Test
    void collapsesInternalRouteCodesToSafeCategories() {
        assertTrue(RouteFailureMessagePolicy.capacityCode("NO_READY_NODE_READY_COUNT_ZERO"));
        assertTrue(RouteFailureMessagePolicy.capacityCode("ACTIVE_NODE_HARD_FULL"));
        assertTrue(RouteFailureMessagePolicy.capacityCode("POOL_EMPTY"));
        assertTrue(RouteFailureMessagePolicy.capacityCode("MAX_ACTIVE_ISLANDS"));
        assertTrue(RouteFailureMessagePolicy.capacityCode("STATE_SOFT_FULL"));
        assertEquals(RouteFailureMessagePolicy.CAPACITY_MESSAGE, RouteFailureMessagePolicy.playerMessage("TARGET_NODE_DOWN", "fallback"));
        assertEquals(RouteFailureMessagePolicy.CAPACITY_MESSAGE, RouteFailureMessagePolicy.playerMessage("HARD_PLAYER_CAP", "fallback"));

        assertTrue(RouteFailureMessagePolicy.maintenanceCode("SESSION_PUBLISH_FAILED"));
        assertTrue(RouteFailureMessagePolicy.maintenanceCode("OBJECT_STORAGE_DOWN"));
        assertTrue(RouteFailureMessagePolicy.maintenanceCode("HEARTBEAT_STALE"));
        assertTrue(RouteFailureMessagePolicy.maintenanceCode("TEMPLATE_UNSUPPORTED"));
        assertTrue(RouteFailureMessagePolicy.maintenanceCode("NODE_VERSION_TOO_OLD"));
        assertEquals(RouteFailureMessagePolicy.MAINTENANCE_MESSAGE, RouteFailureMessagePolicy.playerMessage("ROUTE_STATUS_TIMEOUT", "fallback"));
        assertEquals(RouteFailureMessagePolicy.MAINTENANCE_MESSAGE, RouteFailureMessagePolicy.playerMessage("DEFAULT_NODE_IDENTITY", "fallback"));
    }

    @Test
    void exposesPlayerSafeCategoriesForOperationsMetrics() {
        assertEquals(RouteFailureMessagePolicy.CAPACITY_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("POOL_EMPTY"));
        assertEquals(RouteFailureMessagePolicy.CAPACITY_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("NO_READY_NODE_READY_COUNT_ZERO"));
        assertEquals(RouteFailureMessagePolicy.MAINTENANCE_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("HEARTBEAT_STALE"));
        assertEquals(RouteFailureMessagePolicy.MAINTENANCE_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("SESSION_PUBLISH_FAILED"));
        assertEquals(RouteFailureMessagePolicy.DOMAIN_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("ISLAND_PRIVATE"));
        assertEquals(RouteFailureMessagePolicy.TRANSIENT_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("ISLAND_MIGRATING"));
        assertEquals(RouteFailureMessagePolicy.PERMISSION_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("ADMIN_PERMISSION_DENIED"));
        assertEquals(RouteFailureMessagePolicy.RATE_LIMIT_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("RATE_LIMITED"));
        assertEquals(RouteFailureMessagePolicy.FALLBACK_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory("SOME_INTERNAL_ALLOCATOR_DETAIL"));
        assertEquals(RouteFailureMessagePolicy.FALLBACK_CATEGORY, RouteFailureMessagePolicy.playerSafeCategory(""));
    }
}
