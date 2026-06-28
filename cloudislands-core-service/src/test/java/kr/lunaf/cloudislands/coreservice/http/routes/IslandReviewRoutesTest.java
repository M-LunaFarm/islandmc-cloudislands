package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandReviewModerationSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class IslandReviewRoutesTest {
    @Test
    void registersIslandReviewEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandReviewRoutes routes = new IslandReviewRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/islands/reviews"));
        assertTrue(paths.contains("/v1/islands/reviews/set"));
        assertTrue(paths.contains("/v1/islands/reviews/report"));
        assertTrue(paths.contains("/v1/islands/reviews/delete"));
        assertTrue(paths.contains("/v1/admin/reviews/moderation"));
        assertTrue(paths.contains("/v1/admin/reviews/moderate"));
        assertTrue(paths.contains("/v1/rankings/reviews"));
    }

    @Test
    void registersIslandReviewEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandReviewRoutes(null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/reviews"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/reviews/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/reviews/report"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/reviews/delete"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/reviews/moderation"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/reviews/moderate"));
        assertEquals(Set.of("POST"), registry.methods("/v1/rankings/reviews"));
    }

    @Test
    void rendersReviewModerationContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID reviewerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID moderatorUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        IslandReviewModerationSnapshot moderation = new IslandReviewModerationSnapshot(
            islandId,
            reviewerUuid,
            "reported",
            2,
            "spam report",
            moderatorUuid,
            Instant.parse("2026-01-04T05:06:07Z"),
            "hide if repeated",
            Instant.parse("2026-01-05T06:07:08Z")
        );

        Map<?, ?> accepted = SimpleJson.object(SimpleJson.parse(IslandReviewRoutes.reviewModerationAcceptedJson(moderation)));
        Map<?, ?> acceptedModeration = SimpleJson.object(accepted.get("moderation"));
        Map<?, ?> queue = SimpleJson.object(SimpleJson.parse(IslandReviewRoutes.reviewModerationQueueJson(List.of(moderation))));
        Map<?, ?> queuedModeration = SimpleJson.object(SimpleJson.list(queue.get("reviews")).get(0));

        assertEquals(true, accepted.get("accepted"));
        assertEquals(1, ((Number) queue.get("count")).intValue());
        assertModeration(islandId, reviewerUuid, moderatorUuid, acceptedModeration);
        assertModeration(islandId, reviewerUuid, moderatorUuid, queuedModeration);
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

        Map<?, ?> renderedReview = SimpleJson.object(SimpleJson.parse(IslandReviewRoutes.reviewJson(review)));
        Map<?, ?> accepted = SimpleJson.object(SimpleJson.parse(IslandReviewRoutes.reviewAcceptedJson(review)));
        Map<?, ?> reviewList = SimpleJson.object(SimpleJson.parse(IslandReviewRoutes.reviewsJson(List.of(review))));
        Map<?, ?> listedReview = SimpleJson.object(SimpleJson.list(reviewList.get("reviews")).get(0));
        Map<?, ?> summary = SimpleJson.object(reviewList.get("summary"));
        Map<?, ?> rankings = SimpleJson.object(SimpleJson.parse(
            IslandReviewRoutes.reviewRankingsJson(List.of(new IslandReviewRankSnapshot(islandId, 4.5D, 2, Instant.parse("2026-01-03T04:05:06Z"))))
        ));
        Map<?, ?> ranking = SimpleJson.object(SimpleJson.list(rankings.get("rankings")).get(0));

        assertReview(islandId, reviewerUuid, renderedReview);
        assertEquals(true, accepted.get("accepted"));
        assertReview(islandId, reviewerUuid, SimpleJson.object(accepted.get("review")));
        assertReview(islandId, reviewerUuid, listedReview);
        assertEquals(1, ((Number) summary.get("count")).intValue());
        assertEquals("5.00", SimpleJson.text(summary.get("average")));
        assertEquals(islandId.toString(), SimpleJson.text(ranking.get("islandId")));
        assertEquals("4.50", SimpleJson.text(ranking.get("averageRating")));
        assertEquals(2, ((Number) ranking.get("reviewCount")).intValue());
        assertEquals("2026-01-03T04:05:06Z", SimpleJson.text(ranking.get("updatedAt")));
    }

    private static void assertReview(UUID islandId, UUID reviewerUuid, Map<?, ?> review) {
        assertEquals(islandId.toString(), SimpleJson.text(review.get("islandId")));
        assertEquals(reviewerUuid.toString(), SimpleJson.text(review.get("reviewerUuid")));
        assertEquals(5, ((Number) review.get("rating")).intValue());
        assertEquals("great \"shop\"", SimpleJson.text(review.get("comment")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(review.get("createdAt")));
        assertEquals("2026-01-03T04:05:06Z", SimpleJson.text(review.get("updatedAt")));
    }

    private static void assertModeration(UUID islandId, UUID reviewerUuid, UUID moderatorUuid, Map<?, ?> moderation) {
        assertEquals(islandId.toString(), SimpleJson.text(moderation.get("islandId")));
        assertEquals(reviewerUuid.toString(), SimpleJson.text(moderation.get("reviewerUuid")));
        assertEquals("REPORTED", SimpleJson.text(moderation.get("moderationState")));
        assertEquals(2, ((Number) moderation.get("reportCount")).intValue());
        assertEquals("spam report", SimpleJson.text(moderation.get("reportReason")));
        assertEquals(moderatorUuid.toString(), SimpleJson.text(moderation.get("moderatedBy")));
        assertEquals("2026-01-04T05:06:07Z", SimpleJson.text(moderation.get("moderatedAt")));
        assertEquals("hide if repeated", SimpleJson.text(moderation.get("moderationNote")));
        assertEquals("2026-01-05T06:07:08Z", SimpleJson.text(moderation.get("updatedAt")));
    }

    private static final class RecordingRegistry implements CoreRouteRegistry {
        private final Map<String, Set<String>> methods = new HashMap<>();

        @Override
        public void route(String path, HttpHandler handler) {
            methods.put(path, Set.of("GET", "POST"));
        }

        @Override
        public void routeMethods(String path, HttpHandler handler, String... routeMethods) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            for (String method : routeMethods) {
                allowed.add(method);
            }
            methods.put(path, Set.copyOf(allowed));
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }
    }
}
