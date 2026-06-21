package kr.lunaf.cloudislands.coreclient;

public record ReviewView(String reviewerUuid, long rating, String comment) {
    public ReviewView {
        reviewerUuid = reviewerUuid == null ? "" : reviewerUuid;
        comment = comment == null ? "" : comment;
    }
}
