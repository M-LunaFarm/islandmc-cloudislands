package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandMigratedEvent(UUID islandId, String fromNode, String toNode, long fencingToken, Instant occurredAt) implements CloudIslandEvent {}
