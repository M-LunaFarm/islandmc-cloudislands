package kr.lunaf.cloudislands.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RoutePlan;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public interface IslandRoutingService {
    CompletableFuture<RoutePlan> resolveHome(UUID playerUuid);
    CompletableFuture<RoutePlan> resolveVisit(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<RoutePlan> resolveRandomVisit(UUID visitorUuid);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid);
    CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName);
}
