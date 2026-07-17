package kr.lunaf.cloudislands.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.coreclient.ReviewView;
import org.junit.jupiter.api.Test;

class VelocityPlayerProgressionActionsTest {
    @Test
    void reviewEntriesPreferReviewerNamesWithUuidFallback() {
        ReviewView named = new ReviewView("", "33333333-3333-3333-3333-333333333333", 5L, "great", "", "", " ReviewPlayer ");
        ReviewView legacy = new ReviewView("44444444-4444-4444-4444-444444444444", 4L, "");

        assertEquals("ReviewPlayer=5/5 great", VelocityPlayerProgressionActions.reviewEntry(named));
        assertEquals("44444444=4/5", VelocityPlayerProgressionActions.reviewEntry(legacy));
    }
}
