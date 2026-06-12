package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandAccessChangeEvent(UUID islandId, Boolean publicAccess, Boolean locked, Instant occurredAt) implements CloudIslandEvent {}
