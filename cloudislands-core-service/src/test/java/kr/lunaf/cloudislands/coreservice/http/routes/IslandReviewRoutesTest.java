package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;
import org.junit.jupiter.api.Test;

class IslandReviewRoutesTest {
    @Test
    void registersIslandReviewEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandReviewRoutes routes = new IslandReviewRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(4, paths.size());
        assertTrue(paths.contains("/v1/islands/reviews"));
        assertTrue(paths.contains("/v1/islands/reviews/set"));
        assertTrue(paths.contains("/v1/islands/reviews/delete"));
        assertTrue(paths.contains("/v1/rankings/reviews"));
    }

    @Test
    void rendersReviewContractsWithSummary() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID reviewerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandReviewSnapshot review = new IslandReviewSnapshot(
            islandId,
            reviewerUuid,
            5,
            "great \"shop\"",
            Instant.parse("2026-01-02T03:04:05Z"),
            Instant.parse("2026-01-03T04:05:06Z")
        );

        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"reviewerUuid\":\"00000000-0000-0000-0000-000000000002\",\"rating\":5,\"comment\":\"great \\\"shop\\\"\",\"createdAt\":\"2026-01-02T03:04:05Z\",\"updatedAt\":\"2026-01-03T04:05:06Z\"}",
            IslandReviewRoutes.reviewJson(review)
        );
        assertEquals(
            "{\"reviews\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"reviewerUuid\":\"00000000-0000-0000-0000-000000000002\",\"rating\":5,\"comment\":\"great \\\"shop\\\"\",\"createdAt\":\"2026-01-02T03:04:05Z\",\"updatedAt\":\"2026-01-03T04:05:06Z\"}],\"summary\":{\"count\":1,\"average\":5.00}}",
            IslandReviewRoutes.reviewsJson(List.of(review))
        );
        assertEquals(
            "{\"rankings\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"averageRating\":4.50,\"reviewCount\":2,\"updatedAt\":\"2026-01-03T04:05:06Z\"}]}",
            IslandReviewRoutes.reviewRankingsJson(List.of(new IslandReviewRankSnapshot(islandId, 4.5D, 2, Instant.parse("2026-01-03T04:05:06Z"))))
        );
    }
}
