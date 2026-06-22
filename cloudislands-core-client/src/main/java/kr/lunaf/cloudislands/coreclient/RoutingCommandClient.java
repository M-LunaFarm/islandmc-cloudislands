package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public interface RoutingCommandClient {
    CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName);

    CompletableFuture<Optional<RouteTicket>> routeTicketStatus(RouteTicket ticket);

    CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce);

    CompletableFuture<Void> publishRouteSession(RouteTicket ticket);

    CompletableFuture<RoutePublishView> publishRouteSessionResult(RouteTicket ticket);

    CompletableFuture<RouteClearView> clearRoute(RouteTicket ticket, String reason);

    CompletableFuture<RouteClearView> clearRoute(UUID playerUuid, UUID ticketId, String reason);
}
