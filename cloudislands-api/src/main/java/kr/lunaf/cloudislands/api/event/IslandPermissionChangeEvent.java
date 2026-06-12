package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;

public record IslandPermissionChangeEvent(UUID islandId, IslandRole role, IslandPermission permission, Boolean allowed, Instant occurredAt) implements CloudIslandEvent {}
