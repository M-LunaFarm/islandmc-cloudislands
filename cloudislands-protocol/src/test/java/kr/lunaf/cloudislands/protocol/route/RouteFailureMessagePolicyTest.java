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
        assertEquals(RouteFailureMessagePolicy.CAPACITY_MESSAGE, RouteFailureMessagePolicy.playerMessage("TARGET_NODE_DOWN", "fallback"));

        assertTrue(RouteFailureMessagePolicy.maintenanceCode("SESSION_PUBLISH_FAILED"));
        assertTrue(RouteFailureMessagePolicy.maintenanceCode("OBJECT_STORAGE_DOWN"));
        assertEquals(RouteFailureMessagePolicy.MAINTENANCE_MESSAGE, RouteFailureMessagePolicy.playerMessage("ROUTE_STATUS_TIMEOUT", "fallback"));
    }
}
