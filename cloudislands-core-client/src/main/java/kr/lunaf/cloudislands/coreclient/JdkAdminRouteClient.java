package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkAdminRouteClient implements AdminRouteClient {
    private final JdkCoreApiClient core;

    JdkAdminRouteClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminRouteDebugView> debug(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return core.postWithResultBody("/v1/admin/routes/debug", CoreJsonPayload.object("playerUuid", playerUuid))
            .thenApply(CoreAdminRouteJson::debug);
    }

    @Override
    public CompletableFuture<Optional<AdminRouteTicketView>> ticket(UUID ticketId) {
        requireId(ticketId, "ticketId");
        return core.postWithResultBody("/v1/admin/routes/ticket", CoreJsonPayload.object("ticketId", ticketId))
            .thenApply(CoreAdminRouteJson::ticket);
    }

    @Override
    public CompletableFuture<Optional<AdminRouteTicketView>> ticketForPlayer(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return core.postWithResultBody("/v1/admin/routes/ticket", CoreJsonPayload.object("playerUuid", playerUuid))
            .thenApply(CoreAdminRouteJson::ticket);
    }

    @Override
    public CompletableFuture<AdminRouteClearView> clear(UUID playerUuid, UUID ticketId) {
        return clear(playerUuid, ticketId, "MANUAL_CLEAR");
    }

    @Override
    public CompletableFuture<AdminRouteClearView> clear(UUID playerUuid, UUID ticketId, String reason) {
        requireId(playerUuid, "playerUuid");
        requireId(ticketId, "ticketId");
        String normalizedReason = reason == null || reason.isBlank() ? "MANUAL_CLEAR" : reason;
        return core.postWithResultBody("/v1/admin/routes/clear", CoreJsonPayload.object("playerUuid", playerUuid, "ticketId", ticketId, "reason", normalizedReason))
            .thenApply(CoreAdminRouteJson::clear);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
