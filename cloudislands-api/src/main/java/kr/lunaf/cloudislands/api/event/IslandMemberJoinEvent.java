package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;

public record IslandMemberJoinEvent(UUID islandId, UUID playerUuid, IslandRole role, Instant occurredAt) implements CloudIslandEvent {}
