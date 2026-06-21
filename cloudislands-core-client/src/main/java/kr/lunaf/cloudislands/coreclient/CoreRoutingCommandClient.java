package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public final class CoreRoutingCommandClient implements RoutingCommandClient {
    private final CoreApiClient delegate;

    public CoreRoutingCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
        requireId(playerUuid, "playerUuid");
        requireId(islandId, "islandId");
        return delegate.createWarpTicket(playerUuid, islandId, warpName == null ? "" : warpName);
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(RouteTicket ticket) {
        requireTicket(ticket);
        return delegate.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce());
    }

    @Override
    public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) {
        return publishRouteSessionResult(ticket).thenApply(_result -> null);
    }

    @Override
    public CompletableFuture<RoutePublishView> publishRouteSessionResult(RouteTicket ticket) {
        requireTicket(ticket);
        return delegate.publishRouteSessionResult(ticket).thenApply(CoreRoutingCommandClient::routePublishResult);
    }

    @Override
    public CompletableFuture<RouteClearView> clearRoute(RouteTicket ticket, String reason) {
        requireTicket(ticket);
        String normalizedReason = reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason;
        return delegate.clearRoute(ticket.playerUuid(), ticket.ticketId(), normalizedReason)
            .thenApply(CoreRoutingCommandClient::routeClearResult);
    }

    private static RouteClearView routeClearResult(String body) {
        String code = body == null || body.isBlank() ? "CLEARED" : body.trim();
        return new RouteClearView(true, code);
    }

    private static RoutePublishView routePublishResult(String body) {
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
