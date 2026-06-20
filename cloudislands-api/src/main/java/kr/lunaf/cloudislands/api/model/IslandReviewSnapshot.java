package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandReviewSnapshot(UUID islandId, UUID reviewerUuid, int rating, String comment, Instant createdAt, Instant updatedAt) {
    public static int normalizeRating(int rating) {
        return Math.max(1, Math.min(5, rating));
    }

    public static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return "";
        }
        String normalized = comment.trim().replace('\n', ' ').replace('\r', ' ');
        return normalized.length() > 280 ? normalized.substring(0, 280) : normalized;
    }
}
