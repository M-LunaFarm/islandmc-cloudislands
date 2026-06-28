package kr.lunaf.cloudislands.coreservice.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryIslandReviewRepositoryTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID REVIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID REPORTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");
    private static final UUID MODERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000804");

    @Test
    void reportsAndHidesReviewsFromPublicListsAndRankings() {
        InMemoryIslandReviewRepository reviews = new InMemoryIslandReviewRepository();
        reviews.upsert(ISLAND_ID, REVIEWER_ID, 5, "useful visit");

        var reported = reviews.report(ISLAND_ID, REVIEWER_ID, REPORTER_ID, "spam").orElseThrow();

        assertEquals("REPORTED", reported.moderationState());
        assertEquals(1, reported.reportCount());
        assertEquals(1, reviews.list(ISLAND_ID, 10).size());
        assertEquals(1, reviews.topByRating(10).size());
        assertEquals(1, reviews.moderationQueue(10).size());

        var hidden = reviews.moderate(ISLAND_ID, REVIEWER_ID, "HIDDEN", MODERATOR_ID, "confirmed spam").orElseThrow();

        assertEquals("HIDDEN", hidden.moderationState());
        assertTrue(reviews.list(ISLAND_ID, 10).isEmpty());
        assertTrue(reviews.topByRating(10).isEmpty());
        assertEquals(1, reviews.moderationQueue(10).size());
    }
}
