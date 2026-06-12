package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandMigrationEvent(UUID islandId, boolean requested, String targetNode, String phase, String worldName, long fencingToken, Instant occurredAt) implements CloudIslandEvent {}
