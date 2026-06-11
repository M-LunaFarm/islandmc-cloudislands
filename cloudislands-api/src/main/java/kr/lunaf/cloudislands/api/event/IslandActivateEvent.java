package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandActivateEvent(UUID islandId, String nodeId, String worldName, Instant occurredAt) implements CloudIslandEvent {}
