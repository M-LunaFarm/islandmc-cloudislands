package kr.lunaf.cloudislands.common.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteFailurePolicyTest {
    @Test
    void exposesVisitRejectionCodesAndPlayerSafeMessages() {
        assertTrue(RouteFailurePolicy.PUBLIC_FAILURE_CODES.contains("ISLAND_PRIVATE"));
        assertTrue(RouteFailurePolicy.PUBLIC_FAILURE_CODES.contains("VISITOR_BANNED"));
        assertTrue(RouteFailurePolicy.PUBLIC_FAILURE_CODES.contains("NODE_UNAVAILABLE"));
        assertTrue(RouteFailurePolicy.PUBLIC_FAILURE_CODES.contains("ISLAND_LOADING_FAILED"));
        assertEquals("해당 섬은 비공개 상태입니다.", RouteFailurePolicy.publicMessage("ISLAND_PRIVATE"));
        assertEquals("해당 섬에 방문할 수 없습니다.", RouteFailurePolicy.publicMessage("VISITOR_BANNED"));
        assertEquals("현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.", RouteFailurePolicy.publicMessage("NODE_UNAVAILABLE"));
        assertFalse(RouteFailurePolicy.debugReasonPlayerVisible());
        assertEquals("raw-routing-block-reason-visible-only-in-admin-route-debug-events", RouteFailurePolicy.DEBUG_REASON_POLICY);
    }
}
