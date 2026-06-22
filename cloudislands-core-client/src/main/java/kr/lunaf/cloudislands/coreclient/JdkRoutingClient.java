package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;

final class JdkRoutingClient implements RoutingCommandClient {
    private final JdkCoreApiClient core;

    JdkRoutingClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
        requireId(playerUuid, "playerUuid");
        requireId(islandId, "islandId");
        return core.createWarpTicket(playerUuid, islandId, warpName == null ? "" : warpName);
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(RouteTicket ticket) {
        requireTicket(ticket);
        return core.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce());
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        requireId(ticketId, "ticketId");
        requireId(playerUuid, "playerUuid");
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return core.consumeTicket(ticketId, playerUuid, nodeId.trim(), nonce == null ? "" : nonce);
    }

    @Override
    public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) {
        return publishRouteSessionResult(ticket).thenApply(_result -> null);
    }

    @Override
    public CompletableFuture<RoutePublishView> publishRouteSessionResult(RouteTicket ticket) {
        requireTicket(ticket);
        String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
        return core.postResultBody("/v1/routes/session", CoreJsonPayload.object(
                "playerUuid", ticket.playerUuid(),
                "ticketId", ticket.ticketId(),
                "targetNode", ticket.targetNode(),
                "targetServerName", targetServerName,
                "nonce", ticket.nonce(),
                "expiresAt", ticket.expiresAt()
            ))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkRoutingClient::routePublishResult);
    }

    @Override
    public CompletableFuture<RouteClearView> clearRoute(RouteTicket ticket, String reason) {
        requireTicket(ticket);
        return clearRoute(ticket.playerUuid(), ticket.ticketId(), reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason);
    }

    @Override
    public CompletableFuture<RouteClearView> clearRoute(UUID playerUuid, UUID ticketId, String reason) {
        requireId(playerUuid, "playerUuid");
        requireId(ticketId, "ticketId");
        String normalizedReason = reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason;
        return core.postResultBody("/v1/admin/routes/clear", CoreJsonPayload.object("playerUuid", playerUuid, "ticketId", ticketId, "reason", normalizedReason))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkRoutingClient::routeClearResult);
    }

    static RouteClearView routeClearResult(String body) {
        String code = body == null || body.isBlank() ? "CLEARED" : body.trim();
        return new RouteClearView(true, code);
    }

    static RoutePublishView routePublishResult(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new RoutePublishView(CoreJson.accepted(root), CoreJson.code(root, "ROUTE_SESSION_PUBLISHED"));
    }

    private static void requireTicket(RouteTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("ticket is required");
        }
        requireId(ticket.ticketId(), "ticketId");
        requireId(ticket.playerUuid(), "playerUuid");
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
