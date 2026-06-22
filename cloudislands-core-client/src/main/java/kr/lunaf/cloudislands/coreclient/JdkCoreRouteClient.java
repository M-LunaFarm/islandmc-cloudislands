package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

final class JdkCoreRouteClient {
    private final JdkCoreApiClient core;

    JdkCoreRouteClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) {
        return createHomeTicket(playerUuid, "default");
    }

    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) {
        return core.postWithResultBody("/v1/routes/home", JdkCoreApiClient.jsonObject("playerUuid", playerUuid, "homeName", homeName))
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) {
        return core.postWithResultBody("/v1/routes/visit", JdkCoreApiClient.jsonObject("playerUuid", visitorUuid, "islandId", targetIslandId))
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) {
        return core.postWithResultBody("/v1/routes/visit", JdkCoreApiClient.jsonObject("playerUuid", visitorUuid, "islandName", islandName))
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) {
        return core.postWithResultBody("/v1/routes/visit", JdkCoreApiClient.jsonObject("playerUuid", visitorUuid, "ownerUuid", ownerUuid))
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) {
        return core.postWithResultBody("/v1/routes/random", JdkCoreApiClient.jsonObject("playerUuid", visitorUuid))
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
        return core.postWithResultBody("/v1/routes/warp", JdkCoreApiClient.jsonObject("playerUuid", playerUuid, "islandId", islandId, "warpName", warpName))
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    CompletableFuture<RouteTicket> createMigrationReturnTicket(UUID playerUuid, UUID islandId, String targetNode, double localX, double localY, double localZ, float yaw, float pitch) {
        return core.postWithResultBody("/v1/routes/migration-return", JdkCoreApiClient.jsonObject(
            "playerUuid", playerUuid,
            "islandId", islandId,
            "targetNode", targetNode,
            "localX", localX,
            "localY", localY,
            "localZ", localZ,
            "yaw", yaw,
            "pitch", pitch
        )).thenApply(CoreRouteJson::routeTicketResult);
    }

    CompletableFuture<Optional<PlayerRouteSession>> findRouteSession(UUID playerUuid, String nodeId) {
        return core.post("/v1/routes/session/find", JdkCoreApiClient.jsonObject("playerUuid", playerUuid, "nodeId", nodeId))
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    CompletableFuture<Optional<PlayerRouteSession>> findAnyRouteSession(UUID playerUuid) {
        return core.post("/v1/routes/session/find-any", JdkCoreApiClient.jsonObject("playerUuid", playerUuid))
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId) {
        return consumeRouteSession(playerUuid, nodeId, true);
    }

    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, boolean reportMissing) {
        return core.post("/v1/routes/session/consume", JdkCoreApiClient.jsonObject("playerUuid", playerUuid, "nodeId", nodeId, "reportMissing", reportMissing))
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, UUID ticketId, String nonce, boolean reportMissing) {
        return core.post("/v1/routes/session/consume", JdkCoreApiClient.jsonObject("playerUuid", playerUuid, "nodeId", nodeId, "ticketId", ticketId, "nonce", nonce, "reportMissing", reportMissing))
            .thenApply(JdkCoreRouteClient::routeSession);
    }

    CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce) {
        return core.post("/v1/routes/ticket-status", JdkCoreApiClient.jsonObject("ticketId", ticketId, "playerUuid", playerUuid, "nonce", nonce))
            .thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(CoreRouteJson.routeTicket(body)));
    }

    CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        return core.post("/v1/routes/consume", JdkCoreApiClient.jsonObject("ticketId", ticketId, "playerUuid", playerUuid, "nodeId", nodeId, "nonce", nonce))
            .thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(CoreRouteJson.routeTicket(body)));
    }

    CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId) {
        return core.postWithResultBody("/v1/admin/islands/tp", JdkCoreApiClient.jsonObject("playerUuid", playerUuid, "islandId", islandId))
            .thenApply(CoreRouteJson::routeTicketResult);
    }

    private static Optional<PlayerRouteSession> routeSession(String body) {
        return body.isBlank() ? Optional.empty() : Optional.of(CoreRouteJson.routeSession(body));
    }
}
