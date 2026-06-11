package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteTicketConsumedEvent(UUID islandId, UUID ticketId, UUID playerUuid, Instant occurredAt) implements CloudIslandEvent {}
