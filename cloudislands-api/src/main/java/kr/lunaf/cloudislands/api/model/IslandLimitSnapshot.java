package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandLimitSnapshot(
    UUID islandId,
    String limitKey,
    long value,
    UUID updatedBy,
    Instant updatedAt
) {}
