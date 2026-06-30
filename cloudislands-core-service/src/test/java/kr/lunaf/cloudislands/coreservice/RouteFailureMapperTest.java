package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteFailureMapperTest {
    @Test
    void mapsActiveNodeFailuresToPublicNodeUnavailableWithDiagnostics() {
        RouteFailureMapper.RouteFailureResponse response = RouteFailureMapper.map(
            RouteFailureCode.ACTIVE_NODE_UNAVAILABLE,
            "ACTIVE_NODE_HEARTBEAT_STALE",
            "island-2"
        );

        assertEquals(409, response.status());
        assertEquals("NODE_UNAVAILABLE", response.publicReason());
        assertEquals("island-2", response.targetNode());
        assertEquals("ACTIVE_NODE_HEARTBEAT_STALE", response.debugReason());
        assertTrue(response.includeRoutingDetails());
    }

    @Test
    void mapsNoReadyNodeFailuresToPublicNodeUnavailableWithDiagnostics() {
        RouteFailureMapper.RouteFailureResponse response = RouteFailureMapper.map(
            RouteFailureCode.NO_READY_NODE,
            "NO_READY_NODE_POOL_MISMATCH",
            ""
        );

        assertEquals(409, response.status());
        assertEquals("NODE_UNAVAILABLE", response.publicReason());
        assertEquals("NO_READY_NODE_POOL_MISMATCH", response.debugReason());
        assertTrue(response.includeRoutingDetails());
    }

    @Test
    void mapsSoftFullAndActivationLockWithoutRoutingDiagnostics() {
        RouteFailureMapper.RouteFailureResponse softFull = RouteFailureMapper.map(RouteFailureCode.VISITOR_SOFT_FULL, "", "");
        RouteFailureMapper.RouteFailureResponse activationLocked = RouteFailureMapper.map(RouteFailureCode.ACTIVATION_LOCKED, "", "");

        assertEquals(429, softFull.status());
        assertEquals("VISITOR_SOFT_FULL", softFull.publicReason());
        assertFalse(softFull.includeRoutingDetails());
        assertEquals(409, activationLocked.status());
        assertEquals("ACTIVATION_LOCKED", activationLocked.publicReason());
        assertFalse(activationLocked.includeRoutingDetails());
    }
}
