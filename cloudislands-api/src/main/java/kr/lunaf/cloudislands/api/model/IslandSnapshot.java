package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandSnapshot(
    UUID islandId,
    UUID ownerUuid,
    String name,
    IslandState state,
    int size,
    long level,
    String worth,
    boolean publicAccess,
    Instant createdAt,
    Instant updatedAt
) {}
