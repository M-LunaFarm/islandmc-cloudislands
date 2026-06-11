package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class RouteTicketConsumer {
    private final CoreApiClient coreApiClient;
    private final String nodeId;

    public RouteTicketConsumer(CoreApiClient coreApiClient, String nodeId) {
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
    }

    public void consumeAndTeleport(UUID ticketId, UUID playerUuid, String nonce) {
        coreApiClient.consumeTicket(ticketId, playerUuid, nodeId, nonce).thenAccept(ticket -> {
            if (ticket.isPresent()) {
                teleportOnMainThread(playerUuid);
            }
        });
    }

    private void teleportOnMainThread(UUID playerUuid) {
        // Adapter boundary: Paper scheduler and teleport call are bound here.
    }
}
