package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteSessionPublishedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String targetServerName, Instant occurredAt) implements CloudGlobalEvent {
    public RouteSessionPublishedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, Instant occurredAt) {
        this(ticketId, islandId, playerUuid, action, targetNode, "", occurredAt);
    }
}
