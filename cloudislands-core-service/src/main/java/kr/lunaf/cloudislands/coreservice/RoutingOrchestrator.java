package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public final class RoutingOrchestrator {
    private final InMemoryNodeRegistry nodes;
    private final NodeAllocator allocator;

    public RoutingOrchestrator(InMemoryNodeRegistry nodes, NodeAllocator allocator) {
        this.nodes = nodes;
        this.allocator = allocator;
    }

    public String prepareHomeRouteJson() {
        return toJson(ticket(RouteAction.HOME));
    }

    public String prepareVisitRouteJson() {
        return toJson(ticket(RouteAction.VISIT));
    }

    private RouteTicket ticket(RouteAction action) {
        NodeLoad selected = allocator.selectBestNode(nodes.snapshot(), Instant.now())
            .orElseThrow(() -> new IllegalStateException("no eligible island node"));
        return new RouteTicket(
            UUID.randomUUID(),
            new UUID(0L, 0L),
            action,
            UUID.randomUUID(),
            selected.nodeId(),
            "ci_shard_001",
            RouteTicketState.READY,
            Instant.now().plusSeconds(30),
            UUID.randomUUID().toString(),
            Map.of("targetServerName", selected.velocityServerName())
        );
    }

    public static String toJson(RouteTicket ticket) {
        return "{"
            + "\"ticketId\":\"" + ticket.ticketId() + "\","
            + "\"playerUuid\":\"" + ticket.playerUuid() + "\","
            + "\"action\":\"" + ticket.action() + "\","
            + "\"islandId\":\"" + ticket.islandId() + "\","
            + "\"targetNode\":\"" + ticket.targetNode() + "\","
            + "\"targetServerName\":\"" + ticket.payload().getOrDefault("targetServerName", ticket.targetNode()) + "\","
            + "\"targetWorld\":\"" + ticket.targetWorld() + "\","
            + "\"state\":\"" + ticket.state() + "\","
            + "\"expiresAt\":\"" + ticket.expiresAt() + "\","
            + "\"nonce\":\"" + ticket.nonce() + "\""
            + "}";
    }
}
