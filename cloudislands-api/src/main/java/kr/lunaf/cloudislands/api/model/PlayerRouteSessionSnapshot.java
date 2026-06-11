package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record PlayerRouteSessionSnapshot(
    UUID playerUuid,
    UUID ticketId,
    String targetNode,
    String targetServerName,
    String nonce,
    Instant expiresAt
) {}
