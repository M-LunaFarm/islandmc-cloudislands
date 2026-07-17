package kr.lunaf.cloudislands.coreclient;

public record ReviewModerationView(
    String islandId,
    String reviewerUuid,
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
        reviewerUuid = text(reviewerUuid);
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
