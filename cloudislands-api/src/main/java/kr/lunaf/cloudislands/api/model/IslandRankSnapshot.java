package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandRankSnapshot(UUID islandId, long level, String worth, Instant calculatedAt) {}
