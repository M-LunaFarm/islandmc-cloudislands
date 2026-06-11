package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandCreatedEvent(UUID islandId, UUID ownerUuid, Instant occurredAt) implements CloudIslandEvent {}
