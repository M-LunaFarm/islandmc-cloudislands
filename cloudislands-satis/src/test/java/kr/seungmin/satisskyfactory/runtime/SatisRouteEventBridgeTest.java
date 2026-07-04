package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SatisRouteEventBridgeTest {
    @Test
    void routeEventStateKeepsTopologyHiddenAndIncludesCounters() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-00000000a701");
        Map<String, String> state = SatisRouteEventBridge.routeState(
                new SatisRouteEventBridge.RouteEventSnapshot(
                        "failed",
                        UUID.fromString("00000000-0000-0000-0000-00000000a702"),
                        islandId,
                        UUID.fromString("00000000-0000-0000-0000-00000000a703"),
                        "visit",
                        "paper-1",
                        "island-1",
                        "paper-2",
                        "timeout",
                        "state=FAILED",
                        Instant.parse("2026-07-04T00:00:00Z")
                ),
                "route-authority",
                "ticket-privacy",
                new SatisRouteEventBridge.RouteEventCounters(3L, 2L, 1L, "route-events-feature-disabled")
        );

        assertEquals("failed", state.get("last-route-event"));
        assertEquals(islandId.toString(), state.get("last-route-island"));
        assertEquals("hidden", state.get("last-route-player-visible-topology"));
        assertEquals("hidden", state.get("last-route-ticket-player-visible"));
        assertEquals("ticket-privacy", state.get("last-route-player-visible-policy"));
        assertEquals("3", state.get("route-event-handled-count"));
        assertEquals("2", state.get("route-event-blocked-count"));
        assertEquals("1", state.get("route-event-publish-failures"));
        assertEquals("route-events-feature-disabled", state.get("route-event-last-block-reason"));
    }

    @Test
    void nodeStateUsesRouteCountersForDiagnostics() {
        Map<String, String> state = SatisRouteEventBridge.nodeState(
                new SatisRouteEventBridge.NodeStateSnapshot(
                        "paper-1",
                        "RECOVERY_REQUIRED",
                        "quarantine",
                        "heartbeat-timeout",
                        2,
                        3,
                        4,
                        Instant.parse("2026-07-04T00:00:00Z")
                ),
                new SatisRouteEventBridge.RouteEventCounters(5L, 6L, 7L, "addon-state-feature-disabled")
        );

        assertEquals("paper-1", state.get("last-node-id"));
        assertEquals("RECOVERY_REQUIRED", state.get("last-node-state"));
        assertEquals("diagnostic-state-only-core-keeps-route-authority", state.get("last-node-policy"));
        assertEquals("5", state.get("route-event-handled-count"));
        assertEquals("6", state.get("route-event-blocked-count"));
        assertEquals("7", state.get("route-event-publish-failures"));
        assertEquals("addon-state-feature-disabled", state.get("route-event-last-block-reason"));
    }
}
