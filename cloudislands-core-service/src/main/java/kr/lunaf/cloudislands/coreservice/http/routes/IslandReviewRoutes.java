package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandReviewModerationSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.review.IslandReviewRepository;

public final class IslandReviewRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandReviewRepository reviews;
    private final IslandRepository islands;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandReviewRoutes(IslandReviewRepository reviews, IslandRepository islands, IslandLogRepository islandLogs, AuditLogger audit, GlobalEventPublisher events) {
        this.reviews = reviews;
        this.islands = islands;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/islands/reviews", this::listReviews);
        registry.routePost("/v1/islands/reviews/set", this::setReview);
        registry.routePost("/v1/islands/reviews/report", this::reportReview);
        registry.routePost("/v1/islands/reviews/delete", this::deleteReview);
        registry.routePost("/v1/admin/reviews/moderation", this::reviewModeration);
        registry.routePost("/v1/admin/reviews/moderate", this::moderateReview);
        registry.routePost("/v1/rankings/reviews", this::reviewRankings);
    }

    private void reviewRankings(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 10), 100));
        CoreHttpResponses.write(exchange, 200, reviewRankingsJson(reviews.topByRating(limit)));
    }

    private void listReviews(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 10), 100));
        if (islands.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        CoreHttpResponses.write(exchange, 200, reviewsJson(reviews.list(islandId, limit)));
    }

    private void setReview(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID reviewerUuid = JsonFields.uuid(body, "reviewerUuid", EMPTY_UUID);
        int rating = JsonFields.integer(body, "rating", 0);
        String comment = JsonFields.text(body, "comment", "");
        var island = islands.findById(islandId);
        if (island.isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        if (reviewerUuid.equals(EMPTY_UUID)) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("REVIEWER_REQUIRED", "Reviewer UUID is required"));
            return;
        }
        if (island.get().ownerUuid().equals(reviewerUuid)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("REVIEW_OWNER_DENIED", "Island owners cannot review their own island"));
            return;
        }
        if (rating < 1 || rating > 5) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("REVIEW_RATING_INVALID", "Review rating must be between 1 and 5"));
            return;
        }
        IslandReviewSnapshot review = reviews.upsert(islandId, reviewerUuid, rating, comment);
        audit.log(reviewerUuid, "PLAYER", "ISLAND_REVIEW_SET", "ISLAND", islandId.toString(), Map.of("rating", Integer.toString(review.rating())));
        islandLogs.append(islandId, reviewerUuid, "ISLAND_REVIEW_SET", Map.of("rating", Integer.toString(review.rating())));
        events.publish(CloudIslandEventType.ISLAND_REVIEW_CHANGED.name(), Map.of(
            "islandId", islandId.toString(),
            "reviewerUuid", reviewerUuid.toString(),
            "rating", Integer.toString(review.rating()),
            "operation", "REVIEW_SET"
        ));
        CoreHttpResponses.write(exchange, 202, reviewAcceptedJson(review));
    }

    private void deleteReview(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID reviewerUuid = JsonFields.uuid(body, "reviewerUuid", EMPTY_UUID);
        boolean removed = reviews.delete(islandId, reviewerUuid);
        audit.log(reviewerUuid, "PLAYER", "ISLAND_REVIEW_DELETE", "ISLAND", islandId.toString(), Map.of("removed", Boolean.toString(removed)));
        if (removed) {
            islandLogs.append(islandId, reviewerUuid, "ISLAND_REVIEW_DELETE", Map.of("removed", "true"));
            events.publish(CloudIslandEventType.ISLAND_REVIEW_CHANGED.name(), Map.of(
                "islandId", islandId.toString(),
                "reviewerUuid", reviewerUuid.toString(),
                "operation", "REVIEW_DELETE"
            ));
        }
        CoreHttpResponses.write(exchange, removed ? 202 : 404, removed ? ApiResponses.ok(true) : ApiResponses.error("REVIEW_NOT_FOUND", "Island review was not found"));
    }

    private void reportReview(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID reviewerUuid = JsonFields.uuid(body, "reviewerUuid", EMPTY_UUID);
        UUID reporterUuid = JsonFields.uuid(body, "reporterUuid", EMPTY_UUID);
        String reason = JsonFields.text(body, "reason", "");
        if (reporterUuid.equals(EMPTY_UUID)) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("REPORTER_REQUIRED", "Reporter UUID is required"));
            return;
        }
        var moderation = reviews.report(islandId, reviewerUuid, reporterUuid, reason);
        if (moderation.isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("REVIEW_NOT_FOUND", "Island review was not found"));
            return;
        }
        audit.log(reporterUuid, "PLAYER", "ISLAND_REVIEW_REPORT", "ISLAND", islandId.toString(), Map.of("reviewerUuid", reviewerUuid.toString(), "reportCount", Integer.toString(moderation.get().reportCount())));
        islandLogs.append(islandId, reporterUuid, "ISLAND_REVIEW_REPORT", Map.of("reviewerUuid", reviewerUuid.toString(), "reportCount", Integer.toString(moderation.get().reportCount())));
        events.publish(CloudIslandEventType.ISLAND_REVIEW_CHANGED.name(), Map.of(
            "islandId", islandId.toString(),
            "reviewerUuid", reviewerUuid.toString(),
            "operation", "REVIEW_REPORT",
            "moderationState", moderation.get().moderationState()
        ));
        CoreHttpResponses.write(exchange, 202, reviewModerationAcceptedJson(moderation.get()));
    }

    private void reviewModeration(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 10), 100));
        CoreHttpResponses.write(exchange, 200, reviewModerationQueueJson(reviews.moderationQueue(limit)));
    }

    private void moderateReview(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID reviewerUuid = JsonFields.uuid(body, "reviewerUuid", EMPTY_UUID);
        UUID moderatorUuid = JsonFields.uuid(body, "moderatorUuid", EMPTY_UUID);
        String state = IslandReviewModerationSnapshot.normalizeState(JsonFields.text(body, "moderationState", "VISIBLE"));
        String note = JsonFields.text(body, "note", "");
        var moderation = reviews.moderate(islandId, reviewerUuid, state, moderatorUuid, note);
        if (moderation.isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("REVIEW_NOT_FOUND", "Island review was not found"));
            return;
        }
        audit.log(moderatorUuid, "ADMIN", "ISLAND_REVIEW_MODERATE", "ISLAND", islandId.toString(), Map.of("reviewerUuid", reviewerUuid.toString(), "moderationState", moderation.get().moderationState()));
        islandLogs.append(islandId, moderatorUuid, "ISLAND_REVIEW_MODERATE", Map.of("reviewerUuid", reviewerUuid.toString(), "moderationState", moderation.get().moderationState()));
        events.publish(CloudIslandEventType.ISLAND_REVIEW_CHANGED.name(), Map.of(
            "islandId", islandId.toString(),
            "reviewerUuid", reviewerUuid.toString(),
            "operation", "REVIEW_MODERATE",
            "moderationState", moderation.get().moderationState()
        ));
        CoreHttpResponses.write(exchange, 202, reviewModerationAcceptedJson(moderation.get()));
    }

    static String reviewsJson(List<IslandReviewSnapshot> reviews) {
        int count = reviews.size();
        double average = reviews.stream().mapToInt(IslandReviewSnapshot::rating).average().orElse(0.0D);
        List<Object> renderedReviews = new ArrayList<>();
        for (IslandReviewSnapshot review : reviews) {
            renderedReviews.add(reviewMap(review));
        }
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", count);
        summary.put("average", new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", average)));
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("reviews", renderedReviews);
        values.put("summary", summary);
        return SimpleJson.stringify(values);
    }

    static String reviewJson(IslandReviewSnapshot review) {
        return SimpleJson.stringify(reviewMap(review));
    }

    static String reviewAcceptedJson(IslandReviewSnapshot review) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("review", reviewMap(review));
        return SimpleJson.stringify(values);
    }

    static String reviewRankingsJson(List<IslandReviewRankSnapshot> rankings) {
        List<Object> renderedRankings = new ArrayList<>();
        for (IslandReviewRankSnapshot ranking : rankings) {
            LinkedHashMap<String, Object> rendered = new LinkedHashMap<>();
            rendered.put("islandId", ranking.islandId());
            rendered.put("averageRating", new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", ranking.averageRating())));
            rendered.put("reviewCount", ranking.reviewCount());
            rendered.put("updatedAt", ranking.updatedAt());
            renderedRankings.add(rendered);
        }
        return SimpleJson.stringify(Map.of("rankings", renderedRankings));
    }

    static String reviewModerationAcceptedJson(IslandReviewModerationSnapshot moderation) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("moderation", reviewModerationMap(moderation));
        return SimpleJson.stringify(values);
    }

    static String reviewModerationQueueJson(List<IslandReviewModerationSnapshot> queue) {
        List<Object> rendered = new ArrayList<>();
        for (IslandReviewModerationSnapshot moderation : queue) {
            rendered.add(reviewModerationMap(moderation));
        }
        return SimpleJson.stringify(Map.of("reviews", rendered, "count", rendered.size()));
    }

    private static Map<String, Object> reviewMap(IslandReviewSnapshot review) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", review.islandId());
        values.put("reviewerUuid", review.reviewerUuid());
        values.put("rating", review.rating());
        values.put("comment", review.comment());
        values.put("createdAt", review.createdAt());
        values.put("updatedAt", review.updatedAt());
        return values;
    }

    private static Map<String, Object> reviewModerationMap(IslandReviewModerationSnapshot moderation) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", moderation.islandId());
        values.put("reviewerUuid", moderation.reviewerUuid());
        values.put("moderationState", moderation.moderationState());
        values.put("reportCount", moderation.reportCount());
        values.put("reportReason", moderation.reportReason());
        values.put("moderatedBy", moderation.moderatedBy());
        values.put("moderatedAt", moderation.moderatedAt());
        values.put("moderationNote", moderation.moderationNote());
        values.put("updatedAt", moderation.updatedAt());
        return values;
    }
}
