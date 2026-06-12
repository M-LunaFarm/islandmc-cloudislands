package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandLimitChangeEvent(UUID islandId, String limitKey, long value, Instant occurredAt) implements CloudIslandEvent {}
