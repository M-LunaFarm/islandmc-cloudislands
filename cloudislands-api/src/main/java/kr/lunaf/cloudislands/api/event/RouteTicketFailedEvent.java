package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteTicketFailedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String requestedNode, String reason, Instant occurredAt) implements CloudGlobalEvent {}
