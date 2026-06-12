package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandRuntimeChangeEvent(UUID islandId, String state, String targetNode, String reason, String error, Instant occurredAt) implements CloudIslandEvent {}
