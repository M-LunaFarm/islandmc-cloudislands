package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandDeleteEvent(UUID islandId, UUID requesterUuid, Instant occurredAt) implements CloudIslandEvent {}
