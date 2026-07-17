package kr.lunaf.cloudislands.coreclient;

public record ReviewView(String islandId, String reviewerUuid, long rating, String comment, String createdAt, String updatedAt, String reviewerName) {
    public ReviewView(String reviewerUuid, long rating, String comment) {
        this("", reviewerUuid, rating, comment, "", "", "");
    }

    public ReviewView(String islandId, String reviewerUuid, long rating, String comment, String createdAt, String updatedAt) {
        this(islandId, reviewerUuid, rating, comment, createdAt, updatedAt, "");
    }

    public ReviewView {
        islandId = islandId == null ? "" : islandId;
        reviewerUuid = reviewerUuid == null ? "" : reviewerUuid;
        comment = comment == null ? "" : comment;
        createdAt = createdAt == null ? "" : createdAt;
        updatedAt = updatedAt == null ? "" : updatedAt;
        reviewerName = reviewerName == null ? "" : reviewerName;
    }
}
