package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandPreVisitEvent(UUID islandId, UUID visitorUuid, Instant occurredAt) implements CloudIslandEvent {}
