package kr.lunaf.cloudislands.coreservice.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryIslandReviewRepositoryTest {
    @Test
    void upsertsOneReviewPerReviewerAndNormalizesComment() {
        InMemoryIslandReviewRepository repository = new InMemoryIslandReviewRepository();
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID reviewerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        repository.upsert(islandId, reviewerUuid, 5, " first ");
        var updated = repository.upsert(islandId, reviewerUuid, 3, " second\nline ");

        assertEquals(1, repository.list(islandId, 10).size());
        assertEquals(3, updated.rating());
        assertEquals("second line", updated.comment());
        assertTrue(repository.find(islandId, reviewerUuid).isPresent());
    }
}
