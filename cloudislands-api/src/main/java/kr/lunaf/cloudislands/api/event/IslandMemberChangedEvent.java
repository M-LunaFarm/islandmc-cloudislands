package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;

public record IslandMemberChangedEvent(UUID islandId, UUID playerUuid, String action, IslandRole oldRole, IslandRole newRole, Instant occurredAt) implements CloudIslandEvent {}
