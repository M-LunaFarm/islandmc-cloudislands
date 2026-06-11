package kr.lunaf.cloudislands.velocity;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class VelocityRoutingController {
    private final CoreApiClient coreApiClient;

    public VelocityRoutingController(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
    }

    public void routeHome(UUID playerUuid) {
        coreApiClient.createHomeTicket(playerUuid).thenAccept(ticket -> {
            if (ticket != null) {
                connectWithTicket(playerUuid, ticket.targetNode());
            }
        });
    }

    public void routeVisit(UUID playerUuid, UUID targetIslandId) {
        coreApiClient.createVisitTicket(playerUuid, targetIslandId).thenAccept(ticket -> {
            if (ticket != null) {
                connectWithTicket(playerUuid, ticket.targetNode());
            }
        });
    }

    private void connectWithTicket(UUID playerUuid, String targetNode) {
        // Adapter boundary: real Velocity connection requests are bound here.
    }
}
