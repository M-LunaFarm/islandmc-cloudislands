package kr.lunaf.cloudislands.testkit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;

public final class FakeRouteTickets {
    private FakeRouteTickets() {}

    public static RouteTicket ready(UUID playerUuid, UUID islandId, String nodeId) {
        return new RouteTicket(UUID.randomUUID(), playerUuid, RouteAction.HOME, islandId, nodeId, "ci_shard_001", RouteTicketState.READY, Instant.now().plusSeconds(30), UUID.randomUUID().toString(), Map.of());
    }
}
