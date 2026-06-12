package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteTicketConsumedGlobalEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, Instant occurredAt) implements CloudGlobalEvent {}
