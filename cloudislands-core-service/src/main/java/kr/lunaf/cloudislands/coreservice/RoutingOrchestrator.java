package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;

public final class RoutingOrchestrator {
    private final InMemoryNodeRegistry nodes;
    private final NodeAllocator allocator;
    private final InMemoryRouteTicketStore tickets;

    public RoutingOrchestrator(InMemoryNodeRegistry nodes, NodeAllocator allocator, InMemoryRouteTicketStore tickets) {
        this.nodes = nodes;
        this.allocator = allocator;
        this.tickets = tickets;
    }

    public String prepareHomeRouteJson(UUID playerUuid) {
        return toJson(tickets.save(ticket(playerUuid, UUID.randomUUID(), RouteAction.HOME)));
    }

    public String prepareVisitRouteJson(UUID playerUuid, UUID islandId) {
        return toJson(tickets.save(ticket(playerUuid, islandId, RouteAction.VISIT)));
    }

    public String consumeTicketJson(String body) {
        return tickets.consume(
            JsonFields.uuid(body, "ticketId", new UUID(0L, 0L)),
            JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)),
            JsonFields.text(body, "nodeId", ""),
            JsonFields.text(body, "nonce", "")
        ).map(RoutingOrchestrator::toJson).orElse("");
    }

    private RouteTicket ticket(UUID playerUuid, UUID islandId, RouteAction action) {
        NodeLoad selected = allocator.selectBestNode(nodes.snapshot(), Instant.now())
            .orElseThrow(() -> new IllegalStateException("no eligible island node"));
        return new RouteTicket(
            UUID.randomUUID(),
            playerUuid,
            action,
            islandId,
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
