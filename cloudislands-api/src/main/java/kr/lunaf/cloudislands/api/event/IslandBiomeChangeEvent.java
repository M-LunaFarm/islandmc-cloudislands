package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandBiomeChangeEvent(UUID islandId, String biomeKey, Instant occurredAt) implements CloudIslandEvent {}
