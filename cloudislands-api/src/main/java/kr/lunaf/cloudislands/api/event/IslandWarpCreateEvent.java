package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;

public record IslandWarpCreateEvent(UUID islandId, String warpName, IslandLocation location, Instant occurredAt) implements CloudIslandEvent {}
