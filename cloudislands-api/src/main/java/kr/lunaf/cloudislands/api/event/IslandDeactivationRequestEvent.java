package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandDeactivationRequestEvent(UUID islandId, String state, Instant occurredAt) implements CloudIslandEvent {}
