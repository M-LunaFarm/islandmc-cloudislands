package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandDeactivateEvent(UUID islandId, String nodeId, Instant occurredAt) implements CloudIslandEvent {}
