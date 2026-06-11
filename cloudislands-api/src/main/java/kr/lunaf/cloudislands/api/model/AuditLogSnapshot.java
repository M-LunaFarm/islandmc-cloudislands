package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogSnapshot(
    UUID id,
    UUID actorUuid,
    String actorType,
    String action,
    String targetType,
    String targetId,
    Map<String, String> payload,
    Instant createdAt
) {}
