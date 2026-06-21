package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AdminRouteClient {
    CompletableFuture<AdminRouteDebugView> debug(UUID playerUuid);

    CompletableFuture<Optional<AdminRouteTicketView>> ticket(UUID ticketId);

    CompletableFuture<Optional<AdminRouteTicketView>> ticketForPlayer(UUID playerUuid);

    CompletableFuture<AdminRouteClearView> clear(UUID playerUuid, UUID ticketId);
}
