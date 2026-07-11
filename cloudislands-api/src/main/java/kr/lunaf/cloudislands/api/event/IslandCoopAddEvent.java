package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

/** Published when a player receives co-op access without joining the permanent island team. */
public record IslandCoopAddEvent(UUID islandId, UUID playerUuid, Instant occurredAt) implements CloudIslandEvent {}
