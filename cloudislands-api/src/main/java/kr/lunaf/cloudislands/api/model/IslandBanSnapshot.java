package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandBanSnapshot(
    UUID islandId,
    UUID bannedUuid,
    UUID actorUuid,
    String reason,
    Instant createdAt,
    Instant expiresAt
) {}
