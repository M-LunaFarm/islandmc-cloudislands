package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ClaimedIslandJobSnapshot(
    UUID jobId,
    String type,
    UUID islandId,
    String targetNode,
    int priority,
    Map<String, String> payload,
    Instant createdAt
) {}
