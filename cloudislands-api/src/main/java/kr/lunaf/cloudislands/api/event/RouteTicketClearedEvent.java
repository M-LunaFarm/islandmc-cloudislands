package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteTicketClearedEvent(UUID ticketId, UUID playerUuid, String targetNode, String reason, boolean clearedSession, boolean clearedTicket, Instant occurredAt) implements CloudGlobalEvent {
    public RouteTicketClearedEvent(UUID ticketId, UUID playerUuid, String reason, boolean clearedSession, boolean clearedTicket, Instant occurredAt) {
        this(ticketId, playerUuid, "", reason, clearedSession, clearedTicket, occurredAt);
    }
}
