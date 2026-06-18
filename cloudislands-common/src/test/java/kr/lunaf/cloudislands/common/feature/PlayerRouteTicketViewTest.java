package kr.lunaf.cloudislands.common.feature;

import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerRouteTicketViewTest {
    @Test
    void hidesTicketSecretsAndPhysicalDestinationFromPlayerView() {
        UUID islandId = UUID.randomUUID();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("surface", "island-visit");
        payload.put("targetServerName", "Island-2");
        payload.put("active-node", "island-2");
        payload.put("targetWorld", "ci_island_shard_03");
        payload.put("route-ticket", "secret");
        payload.put("display-name", "Luna Farm");
        RouteTicket ticket = new RouteTicket(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RouteAction.VISIT,
                islandId,
                "island-2",
                "ci_island_shard_03",
                RouteTicketState.READY,
                Instant.now().plusSeconds(30),
                "nonce-secret",
                payload
        );

        PlayerRouteTicketView view = PlayerRouteTicketView.from(ticket);

        assertEquals(islandId, view.islandId());
        assertEquals(RouteAction.VISIT, view.action());
        assertEquals(RouteTicketState.READY, view.state());
        assertEquals("island-visit", view.destination());
        assertEquals(Map.of(
                "surface", "island-visit",
                "display-name", "Luna Farm"
        ), view.payload());
        assertFalse(view.exposesRouteSecret());
    }

    @Test
    void derivesLogicalDestinationWhenPayloadSurfaceIsMissing() {
        RouteTicket ticket = new RouteTicket(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RouteAction.WARP,
                UUID.randomUUID(),
                "island-3",
                "ci_island_shard_05",
                RouteTicketState.PREPARING,
                Instant.now().plusSeconds(30),
                "nonce-secret",
                Map.of("targetServerName", "Island-3")
        );

        PlayerRouteTicketView view = PlayerRouteTicketView.from(ticket);

        assertEquals("island-warps", view.destination());
        assertEquals(Map.of(), view.payload());
        assertFalse(view.exposesRouteSecret());
    }
}
