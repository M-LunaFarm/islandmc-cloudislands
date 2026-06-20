package kr.lunaf.cloudislands.coreservice.review;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryIslandReviewRepositoryTest {
    @Test
    void ranksIslandsByAverageRatingThenReviewCount() {
        InMemoryIslandReviewRepository repository = new InMemoryIslandReviewRepository();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        repository.upsert(first, UUID.randomUUID(), 5, "");
        repository.upsert(first, UUID.randomUUID(), 4, "");
        repository.upsert(second, UUID.randomUUID(), 5, "");

        var rankings = repository.topByRating(10);

        assertEquals(second, rankings.get(0).islandId());
        assertEquals(5.0D, rankings.get(0).averageRating());
        assertEquals(1, rankings.get(0).reviewCount());
        assertEquals(first, rankings.get(1).islandId());
        assertEquals(4.5D, rankings.get(1).averageRating());
        assertEquals(2, rankings.get(1).reviewCount());
    }
}
