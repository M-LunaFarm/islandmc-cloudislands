package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandRestoreRequestEvent(UUID islandId, String state, String targetNode, long snapshotNo, Instant occurredAt) implements CloudIslandEvent {}
