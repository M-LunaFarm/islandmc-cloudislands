package kr.lunaf.cloudislands.coreservice.ranking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IslandRankSnapshot(UUID islandId, long level, BigDecimal worth, int memberCount, Instant updatedAt) {}
