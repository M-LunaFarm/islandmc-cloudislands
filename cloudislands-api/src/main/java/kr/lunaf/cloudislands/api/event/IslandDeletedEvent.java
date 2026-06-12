package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandDeletedEvent(UUID islandId, long snapshotNo, Instant occurredAt) implements CloudIslandEvent {}
