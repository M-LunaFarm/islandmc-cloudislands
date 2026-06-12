package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandWarpChangeEvent(UUID islandId, String warpName, String operation, Instant occurredAt) implements CloudIslandEvent {}
