package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RouteTicket(
    UUID ticketId,
    UUID playerUuid,
    RouteAction action,
    UUID islandId,
    String targetNode,
    String targetWorld,
    RouteTicketState state,
    Instant expiresAt,
    String nonce,
    Map<String, String> payload
) {}
