package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandBlocksChangeEvent(UUID islandId, String materialKey, String delta, Instant occurredAt) implements CloudIslandEvent {}
