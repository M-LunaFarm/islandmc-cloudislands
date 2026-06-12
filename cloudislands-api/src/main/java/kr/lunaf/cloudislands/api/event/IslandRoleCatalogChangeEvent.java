package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;

public record IslandRoleCatalogChangeEvent(UUID islandId, IslandRole role, String operation, Instant occurredAt) implements CloudIslandEvent {}
