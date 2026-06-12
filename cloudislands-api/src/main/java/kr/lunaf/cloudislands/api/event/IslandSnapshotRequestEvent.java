package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandSnapshotRequestEvent(UUID islandId, String reason, Instant occurredAt) implements CloudIslandEvent {}
