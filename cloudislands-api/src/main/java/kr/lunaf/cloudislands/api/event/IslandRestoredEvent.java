package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandRestoredEvent(UUID islandId, long snapshotNo, String state, Instant occurredAt) implements CloudIslandEvent {}
