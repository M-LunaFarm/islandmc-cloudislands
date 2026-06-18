package kr.lunaf.cloudislands.common.routing;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteReadinessPolicyTest {
    @Test
    void routeTicketReadyRequiresEveryRuntimeAndCacheGate() {
        Map<String, Boolean> gates = RouteReadinessPolicy.gateMap(
                true,
                true,
                true,
                true,
                true,
                true
        );

        assertTrue(RouteReadinessPolicy.ready(gates));
        assertEquals("", RouteReadinessPolicy.firstMissingGate(gates));
        assertEquals("READY", RouteReadinessPolicy.ticketState(gates));
    }

    @Test
    void keepsTicketPreparingUntilSpawnChunkIsPreloaded() {
        Map<String, Boolean> gates = RouteReadinessPolicy.gateMap(
                true,
                true,
                true,
                true,
                false,
                true
        );

        assertFalse(RouteReadinessPolicy.ready(gates));
        assertEquals("spawn-chunk-preloaded", RouteReadinessPolicy.firstMissingGate(gates));
        assertEquals("PREPARING", RouteReadinessPolicy.ticketState(gates));
    }

    @Test
    void documentsPreparationOrderForPlayerHiddenRoutingFlow() {
        assertEquals(
                "runtime-active-or-restored>protection-cache-ready>member-cache-ready>warp-cache-ready>spawn-chunk-preloaded>route-session-publishable",
                RouteReadinessPolicy.requiredGateSummary()
        );
        assertTrue(RouteReadinessPolicy.requiredGates().contains("protection-cache-ready"));
        assertTrue(RouteReadinessPolicy.requiredGates().contains("member-cache-ready"));
        assertTrue(RouteReadinessPolicy.requiredGates().contains("warp-cache-ready"));
        assertTrue(RouteReadinessPolicy.requiredGates().contains("spawn-chunk-preloaded"));
        assertEquals("섬을 준비하는 중입니다...", RouteReadinessPolicy.PLAYER_WAIT_MESSAGE);
        assertEquals(
                "route-ticket-ready-requires-runtime-protection-member-warp-cache-and-spawn-chunk-preload",
                RouteReadinessPolicy.READY_GATE_POLICY
        );
    }
}
