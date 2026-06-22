package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

final class JdkCoreRouteClient implements RouteTicketClient {
    private final JdkCoreApiClient core;

    JdkCoreRouteClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) {
        return createHomeTicket(playerUuid, "default");
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) {
        return core.postResultBody("/v1/routes/home", CoreJsonPayload.object("playerUuid", playerUuid, "homeName", homeName))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) {
        return core.postResultBody("/v1/routes/visit", CoreJsonPayload.object("playerUuid", visitorUuid, "islandId", targetIslandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) {
        return core.postResultBody("/v1/routes/visit", CoreJsonPayload.object("playerUuid", visitorUuid, "islandName", islandName))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) {
        return core.postResultBody("/v1/routes/visit", CoreJsonPayload.object("playerUuid", visitorUuid, "ownerUuid", ownerUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) {
        return core.postResultBody("/v1/routes/random", CoreJsonPayload.object("playerUuid", visitorUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
        return core.postResultBody("/v1/routes/warp", CoreJsonPayload.object("playerUuid", playerUuid, "islandId", islandId, "warpName", warpName))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createMigrationReturnTicket(UUID playerUuid, UUID islandId, String targetNode, double localX, double localY, double localZ, float yaw, float pitch) {
        return core.postResultBody("/v1/routes/migration-return", CoreJsonPayload.object(
            "playerUuid", playerUuid,
            "islandId", islandId,
            "targetNode", targetNode,
            "localX", localX,
            "localY", localY,
            "localZ", localZ,
            "yaw", yaw,
            "pitch", pitch
        ))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> findRouteSession(UUID playerUuid, String nodeId) {
        return core.postBody("/v1/routes/session/find", CoreJsonPayload.object("playerUuid", playerUuid, "nodeId", nodeId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> findAnyRouteSession(UUID playerUuid) {
        return core.postBody("/v1/routes/session/find-any", CoreJsonPayload.object("playerUuid", playerUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId) {
        return consumeRouteSession(playerUuid, nodeId, true);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, boolean reportMissing) {
        return core.postBody("/v1/routes/session/consume", CoreJsonPayload.object("playerUuid", playerUuid, "nodeId", nodeId, "reportMissing", reportMissing))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, UUID ticketId, String nonce, boolean reportMissing) {
        return core.postBody("/v1/routes/session/consume", CoreJsonPayload.object("playerUuid", playerUuid, "nodeId", nodeId, "ticketId", ticketId, "nonce", nonce, "reportMissing", reportMissing))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce) {
        return core.postBody("/v1/routes/ticket-status", CoreJsonPayload.object("ticketId", ticketId, "playerUuid", playerUuid, "nonce", nonce))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(CoreRouteJson.routeTicket(body)));
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        return core.postBody("/v1/routes/consume", CoreJsonPayload.object("ticketId", ticketId, "playerUuid", playerUuid, "nodeId", nodeId, "nonce", nonce))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(CoreRouteJson.routeTicket(body)));
    }

    @Override
    public CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId) {
        return core.postResultBody("/v1/admin/islands/tp", CoreJsonPayload.object("playerUuid", playerUuid, "islandId", islandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    private static Optional<PlayerRouteSession> routeSession(String body) {
        return body.isBlank() ? Optional.empty() : Optional.of(CoreRouteJson.routeSession(body));
    }
}
