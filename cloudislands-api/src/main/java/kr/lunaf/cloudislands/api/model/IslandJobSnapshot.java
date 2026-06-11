package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IslandJobSnapshot(
    UUID jobId,
    String type,
    UUID islandId,
    String targetNode,
    String state,
    int priority,
    int attempts,
    String lockedBy,
    String errorMessage,
    Map<String, String> payload,
    Instant createdAt,
    Instant updatedAt
) {}
