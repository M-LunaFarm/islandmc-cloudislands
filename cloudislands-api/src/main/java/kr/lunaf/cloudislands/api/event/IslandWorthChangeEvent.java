package kr.lunaf.cloudislands.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IslandWorthChangeEvent(UUID islandId, BigDecimal worth, Instant occurredAt) implements CloudIslandEvent {}
