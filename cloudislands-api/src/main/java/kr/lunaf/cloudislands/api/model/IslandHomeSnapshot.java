package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandHomeSnapshot(
    UUID islandId,
    String name,
    IslandLocation location,
    UUID createdBy,
    Instant createdAt
) {}
