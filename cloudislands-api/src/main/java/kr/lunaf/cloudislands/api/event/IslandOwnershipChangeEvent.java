package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandOwnershipChangeEvent(UUID islandId, UUID actorUuid, UUID targetUuid, Instant occurredAt) implements CloudIslandEvent {}
