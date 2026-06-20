package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandReviewRankSnapshot(UUID islandId, double averageRating, int reviewCount, Instant updatedAt) {}
