package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;

public record IslandPermissionCheckEvent(UUID islandId, UUID playerUuid, IslandPermission permission, boolean allowed, Instant occurredAt) implements CloudIslandEvent {}
