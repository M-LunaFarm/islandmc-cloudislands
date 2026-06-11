package kr.lunaf.cloudislands.protocol.job;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IslandJob(UUID jobId, IslandJobType type, UUID islandId, String targetNode, int priority, Map<String, String> payload, Instant createdAt) {}
