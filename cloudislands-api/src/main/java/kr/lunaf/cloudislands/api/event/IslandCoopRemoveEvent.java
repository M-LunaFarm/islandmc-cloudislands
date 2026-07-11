package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

/** Published when a player's co-op access is removed. */
public record IslandCoopRemoveEvent(UUID islandId, UUID playerUuid, Instant occurredAt) implements CloudIslandEvent {}
