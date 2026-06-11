package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IslandLogRecord(
    UUID logId,
    UUID islandId,
    UUID actorUuid,
    String action,
    Map<String, String> payload,
    Instant createdAt
) {}
