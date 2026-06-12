package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandVisitorBanChangeEvent(UUID islandId, UUID playerUuid, boolean banned, Instant occurredAt) implements CloudIslandEvent {}
