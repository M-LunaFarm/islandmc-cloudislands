package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandDeleteRequestEvent(UUID islandId, String targetNode, String reason, Instant occurredAt) implements CloudIslandEvent {}
