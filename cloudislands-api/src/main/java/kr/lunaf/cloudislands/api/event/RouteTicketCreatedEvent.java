package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteTicketCreatedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String targetServerName, String state, Instant occurredAt) implements CloudGlobalEvent {
    public RouteTicketCreatedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String state, Instant occurredAt) {
        this(ticketId, islandId, playerUuid, action, targetNode, "", state, occurredAt);
    }
}
