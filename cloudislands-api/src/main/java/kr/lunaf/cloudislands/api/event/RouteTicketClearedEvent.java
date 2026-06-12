package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record RouteTicketClearedEvent(UUID ticketId, UUID playerUuid, String reason, boolean clearedSession, boolean clearedTicket, Instant occurredAt) implements CloudGlobalEvent {}
