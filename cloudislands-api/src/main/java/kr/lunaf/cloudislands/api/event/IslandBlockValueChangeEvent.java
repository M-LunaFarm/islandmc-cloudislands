package kr.lunaf.cloudislands.api.event;

import java.math.BigDecimal;
import java.time.Instant;

public record IslandBlockValueChangeEvent(String materialKey, BigDecimal worth, long levelPoints, long limit, Instant occurredAt) implements CloudGlobalEvent {}
