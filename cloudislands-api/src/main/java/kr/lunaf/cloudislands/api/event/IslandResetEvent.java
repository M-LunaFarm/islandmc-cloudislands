package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandResetEvent(UUID islandId, boolean requested, String state, String targetNode, String reason, Instant occurredAt) implements CloudIslandEvent {}
