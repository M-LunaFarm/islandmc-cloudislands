package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandPreActivateEvent(UUID islandId, String targetNode, Instant occurredAt) implements CloudIslandEvent {}
