package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.ReviewModerationView;
import org.junit.jupiter.api.Test;

class AdminReviewModerationMenuTest {
    @Test
    void reviewItemsKeepFullActionIdentifiersAndFriendlyNames() {
        UUID islandId = UUID.randomUUID();
        UUID reviewerUuid = UUID.randomUUID();
        ReviewModerationView review = new ReviewModerationView(
            islandId.toString(),
            "Sky Home",
            reviewerUuid.toString(),
            "Reviewer",
            "REPORTED",
            4,
            "spam\nwith control line",
            "",
            "",
            "",
            "now"
        );

        assertEquals(
            Map.of("islandId", islandId.toString(), "reviewerUuid", reviewerUuid.toString()),
            AdminReviewModerationMenu.reviewActionData(review)
        );
        assertTrue(AdminReviewModerationMenu.reviewTitle(review).contains("Sky Home"));
        assertTrue(AdminReviewModerationMenu.reviewTitle(review).contains("Reviewer"));
        assertTrue(AdminReviewModerationMenu.reviewLore(review, null).stream().noneMatch(line -> line.contains("\n")));
    }
}
