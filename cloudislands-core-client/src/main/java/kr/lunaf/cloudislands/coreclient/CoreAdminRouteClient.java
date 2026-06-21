package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreAdminRouteClient implements AdminRouteClient {
    private final CoreApiClient delegate;

    public CoreAdminRouteClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AdminRouteDebugView> debug(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return delegate.debugRoutes(playerUuid).thenApply(CoreAdminRouteJson::debug);
    }

    @Override
    public CompletableFuture<Optional<AdminRouteTicketView>> ticket(UUID ticketId) {
        requireId(ticketId, "ticketId");
        return delegate.routeTicket(ticketId).thenApply(CoreAdminRouteJson::ticket);
    }

    @Override
    public CompletableFuture<Optional<AdminRouteTicketView>> ticketForPlayer(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return delegate.routeTicketForPlayer(playerUuid).thenApply(CoreAdminRouteJson::ticket);
    }

    @Override
    public CompletableFuture<AdminRouteClearView> clear(UUID playerUuid, UUID ticketId) {
        requireId(playerUuid, "playerUuid");
        requireId(ticketId, "ticketId");
        return delegate.clearRouteResult(playerUuid, ticketId).thenApply(CoreAdminRouteJson::clear);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
