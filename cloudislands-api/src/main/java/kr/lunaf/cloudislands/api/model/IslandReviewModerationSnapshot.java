package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record IslandReviewModerationSnapshot(
    UUID islandId,
    UUID reviewerUuid,
    String moderationState,
    int reportCount,
    String reportReason,
    UUID moderatedBy,
    Instant moderatedAt,
    String moderationNote,
    Instant updatedAt
) {
    public IslandReviewModerationSnapshot {
        moderationState = normalizeState(moderationState);
        reportCount = Math.max(0, reportCount);
        reportReason = normalizeText(reportReason, 180);
        moderationNote = normalizeText(moderationNote, 180);
        moderatedAt = moderatedAt == null ? Instant.EPOCH : moderatedAt;
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public static String normalizeState(String state) {
        String normalized = state == null ? "" : state.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "REPORTED", "HIDDEN" -> normalized;
            default -> "VISIBLE";
        };
    }

    public static String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('\n', ' ').replace('\r', ' ');
        int limit = Math.max(1, maxLength);
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }

    public boolean publiclyVisible() {
        return !moderationState.equals("HIDDEN");
    }
}
