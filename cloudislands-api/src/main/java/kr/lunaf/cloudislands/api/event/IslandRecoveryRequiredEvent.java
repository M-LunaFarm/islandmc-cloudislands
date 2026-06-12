package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandRecoveryRequiredEvent(UUID islandId, String nodeId, String reason, Instant occurredAt) implements CloudIslandEvent {}
