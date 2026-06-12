package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandVisitorKickEvent(UUID islandId, UUID playerUuid, UUID actorUuid, Instant occurredAt) implements CloudIslandEvent {}
