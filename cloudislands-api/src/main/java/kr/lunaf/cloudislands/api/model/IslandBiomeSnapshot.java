package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandBiomeSnapshot(
    UUID islandId,
    String biomeKey,
    UUID updatedBy,
    Instant updatedAt
) {}
