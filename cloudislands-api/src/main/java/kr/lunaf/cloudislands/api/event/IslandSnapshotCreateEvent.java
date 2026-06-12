package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandSnapshotCreateEvent(UUID islandId, long snapshotNo, String reason, Instant occurredAt) implements CloudIslandEvent {}
