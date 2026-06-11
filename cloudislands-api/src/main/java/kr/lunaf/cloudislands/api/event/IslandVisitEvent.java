package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandVisitEvent(UUID islandId, UUID visitorUuid, String nodeId, Instant occurredAt) implements CloudIslandEvent {}
