package kr.lunaf.cloudislands.api.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.PlayerRouteSessionSnapshot;
import kr.lunaf.cloudislands.api.model.RoutePlan;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public interface IslandRoutingService {
    CompletableFuture<RoutePlan> resolveHome(UUID playerUuid);
    CompletableFuture<RoutePlan> resolveHome(UUID playerUuid, String homeName);
    CompletableFuture<RoutePlan> resolveVisit(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<RoutePlan> resolveRandomVisit(UUID visitorUuid);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid);
    CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName);
    CompletableFuture<Void> publishRouteSession(RouteTicket ticket);
    CompletableFuture<IslandActionResult> publishRouteSessionResult(RouteTicket ticket);
    CompletableFuture<Optional<PlayerRouteSessionSnapshot>> consumeRouteSession(UUID playerUuid, String nodeId);
    CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce);
    CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce);
}
