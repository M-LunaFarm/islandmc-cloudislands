package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteTicketFailedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String targetServerName, String requestedNode, String reason, Instant occurredAt) implements CloudGlobalEvent {
    public RouteTicketFailedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String requestedNode, String reason, Instant occurredAt) {
        this(ticketId, islandId, playerUuid, action, targetNode, "", requestedNode, reason, occurredAt);
    }
}
