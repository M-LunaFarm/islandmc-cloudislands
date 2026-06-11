package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandLevelRecalculateEvent(UUID islandId, long level, Instant occurredAt) implements CloudIslandEvent {}
