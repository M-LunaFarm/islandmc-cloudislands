package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandCreateEvent(UUID islandId, UUID ownerUuid, String templateId, Instant occurredAt) implements CloudIslandEvent {}
