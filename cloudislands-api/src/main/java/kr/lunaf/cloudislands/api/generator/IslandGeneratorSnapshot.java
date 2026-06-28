package kr.lunaf.cloudislands.api.generator;

import java.time.Instant;
import java.util.UUID;

public record IslandGeneratorSnapshot(UUID islandId, String generatorKey, int level, Instant updatedAt) {
    public IslandGeneratorSnapshot {
        generatorKey = generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey.trim().toLowerCase();
        level = Math.max(1, level);
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }
}
