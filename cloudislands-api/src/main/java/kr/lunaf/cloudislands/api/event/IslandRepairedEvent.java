package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandRepairedEvent(UUID islandId, String reason, Instant occurredAt) implements CloudIslandEvent {}
