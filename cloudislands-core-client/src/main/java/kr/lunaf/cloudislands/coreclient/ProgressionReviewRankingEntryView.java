package kr.lunaf.cloudislands.coreclient;

public record ProgressionReviewRankingEntryView(String islandId, double averageRating, long reviewCount) {
    public ProgressionReviewRankingEntryView {
        islandId = islandId == null ? "" : islandId;
    }
}
