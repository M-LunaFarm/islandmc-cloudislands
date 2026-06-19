package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RouteFallbackServiceTest {
    @Test
    void sanitizesMetricFailureCodes() {
        assertEquals("ROUTE_FAILED", RouteFallbackService.safeFailureCode("", ""));
        assertEquals("TARGET_SERVER_NOT_FOUND", RouteFallbackService.safeFailureCode("TARGET SERVER/NOT FOUND", "ROUTE_FAILED"));
        assertEquals("CORE_API_IO", RouteFallbackService.safeFailureCode("CORE_API_IO", "ROUTE_FAILED"));
    }

    @Test
    void mapsNodeIdToVelocityServerNameFallback() {
        assertEquals("", RouteFallbackService.nodeIdToServerName(null));
        assertEquals("Island-1", RouteFallbackService.nodeIdToServerName("island-1"));
        assertEquals("Island-Main-2", RouteFallbackService.nodeIdToServerName("island-main-2"));
    }
}
