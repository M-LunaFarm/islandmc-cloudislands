package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewView;
import org.junit.jupiter.api.Test;

class IslandReviewMenuTest {
    @Test
    void reviewLorePrefersReviewerNamesWithUuidFallback() {
        assertEquals("ReviewPlayer", IslandReviewMenu.reviewerDisplayName(new ReviewView("33333333-3333-3333-3333-333333333333", 5L, "", " ReviewPlayer ")));
        assertEquals("33333333", IslandReviewMenu.reviewerDisplayName(new ReviewView("33333333-3333-3333-3333-333333333333", 5L, "")));
    }

    @Test
    void reviewReportDataCarriesStableIslandAndReviewerIdentity() {
        UUID islandId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ReviewView review = new ReviewView("33333333-3333-3333-3333-333333333333", 5L, "", " ReviewPlayer ");

        assertEquals(Map.of(
            "islandId", islandId.toString(),
            "reviewerUuid", "33333333-3333-3333-3333-333333333333",
            "reviewerName", "ReviewPlayer"
        ), IslandReviewMenu.reviewReportData(islandId, review));
        assertTrue(IslandReviewMenu.reviewReportData(islandId, new ReviewView("invalid", 4L, "")).isEmpty());
    }
}
