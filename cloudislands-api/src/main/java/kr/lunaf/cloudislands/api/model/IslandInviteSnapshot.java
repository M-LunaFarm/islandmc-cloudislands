package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandInviteSnapshot(
    UUID inviteId,
    UUID islandId,
    UUID inviterUuid,
    UUID targetUuid,
    String state,
    Instant createdAt,
    Instant expiresAt
) {}
