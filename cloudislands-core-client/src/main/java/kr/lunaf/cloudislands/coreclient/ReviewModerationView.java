package kr.lunaf.cloudislands.coreclient;

public record ReviewModerationView(
    String islandId,
    String islandName,
    String reviewerUuid,
    String reviewerName,
    String moderationState,
    int reportCount,
    String reportReason,
    String moderatedBy,
    String moderatedAt,
    String moderationNote,
    String updatedAt
) {
    public ReviewModerationView {
        islandId = text(islandId);
        islandName = text(islandName);
        reviewerUuid = text(reviewerUuid);
        reviewerName = text(reviewerName);
        moderationState = text(moderationState);
        reportCount = Math.max(0, reportCount);
        reportReason = text(reportReason);
        moderatedBy = text(moderatedBy);
        moderatedAt = text(moderatedAt);
        moderationNote = text(moderationNote);
        updatedAt = text(updatedAt);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
