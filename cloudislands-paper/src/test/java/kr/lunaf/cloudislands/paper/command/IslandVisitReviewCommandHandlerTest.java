package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewView;
import org.junit.jupiter.api.Test;

class IslandVisitReviewCommandHandlerTest {
    @Test
    void reviewEntriesPreferReviewerNamesWithUuidFallback() {
        ReviewView named = new ReviewView("33333333-3333-3333-3333-333333333333", 5L, "great", " ReviewPlayer ");
        ReviewView legacy = new ReviewView("44444444-4444-4444-4444-444444444444", 4L, "", "");

        assertEquals("ReviewPlayer=5/5 great", IslandVisitReviewCommandHandler.reviewEntry(named));
        assertEquals("44444444=4/5", IslandVisitReviewCommandHandler.reviewEntry(legacy));
    }
}
