package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandWarpDeleteEvent(UUID islandId, String warpName, Instant occurredAt) implements CloudIslandEvent {}
