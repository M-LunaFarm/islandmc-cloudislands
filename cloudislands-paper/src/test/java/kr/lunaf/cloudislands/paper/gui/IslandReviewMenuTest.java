package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewView;
import org.junit.jupiter.api.Test;

class IslandReviewMenuTest {
    @Test
    void reviewLorePrefersReviewerNamesWithUuidFallback() {
        assertEquals("ReviewPlayer", IslandReviewMenu.reviewerDisplayName(new ReviewView("33333333-3333-3333-3333-333333333333", 5L, "", " ReviewPlayer ")));
        assertEquals("33333333", IslandReviewMenu.reviewerDisplayName(new ReviewView("33333333-3333-3333-3333-333333333333", 5L, "")));
    }
}
