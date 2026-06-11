package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandMemberLeaveEvent(UUID islandId, UUID playerUuid, Instant occurredAt) implements CloudIslandEvent {}
