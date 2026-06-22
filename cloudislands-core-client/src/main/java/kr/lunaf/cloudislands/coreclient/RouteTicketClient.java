package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public interface RouteTicketClient {
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid);

    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName);

    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId);

    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName);

    CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid);

    CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid);

    CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName);

    CompletableFuture<RouteTicket> createMigrationReturnTicket(UUID playerUuid, UUID islandId, String targetNode, double localX, double localY, double localZ, float yaw, float pitch);

    CompletableFuture<Optional<PlayerRouteSession>> findRouteSession(UUID playerUuid, String nodeId);

    CompletableFuture<Optional<PlayerRouteSession>> findAnyRouteSession(UUID playerUuid);

    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId);

    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, boolean reportMissing);

    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, UUID ticketId, String nonce, boolean reportMissing);

    CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce);

    CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce);

    CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId);
}
