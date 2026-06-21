package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record ReviewListView(long count, double average, List<ReviewView> reviews) {
    public ReviewListView {
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }
}
