package kr.lunaf.cloudislands.protocol.session;

import java.time.Instant;
import java.util.UUID;

public record PlayerRouteSession(
    UUID playerUuid,
    UUID ticketId,
    String targetNode,
    String targetServerName,
    String nonce,
    Instant expiresAt
) {}
