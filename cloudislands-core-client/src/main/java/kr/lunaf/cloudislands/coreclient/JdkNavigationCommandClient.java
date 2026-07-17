package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public final class JdkNavigationCommandClient implements NavigationCommandClient {
    private final JdkCoreApiClient core;

    public JdkNavigationCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) {
        requireId(playerUuid, "playerUuid");
        return core.createHomeTicket(playerUuid, homeName == null || homeName.isBlank() ? "default" : homeName.trim());
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID islandId) {
        requireId(visitorUuid, "visitorUuid");
        requireId(islandId, "islandId");
        return core.createVisitTicket(visitorUuid, islandId);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) {
        requireId(visitorUuid, "visitorUuid");
        if (islandName == null || islandName.isBlank()) {
            throw new IllegalArgumentException("islandName is required");
        }
        return core.createVisitTicket(visitorUuid, islandName.trim());
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) {
        requireId(visitorUuid, "visitorUuid");
        requireId(ownerUuid, "ownerUuid");
        return core.createVisitTicketForOwner(visitorUuid, ownerUuid);
    }

    @Override
    public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) {
        requireId(visitorUuid, "visitorUuid");
        return core.createRandomVisitTicket(visitorUuid);
    }

    @Override
    public CompletableFuture<ReviewActionView> setReview(UUID islandId, UUID reviewerUuid, int rating, String comment) {
        requireId(islandId, "islandId");
        requireId(reviewerUuid, "reviewerUuid");
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        return core.postResultBody("/v1/islands/reviews/set", CoreJsonPayload.object("islandId", islandId, "reviewerUuid", reviewerUuid, "rating", rating, "comment", comment == null ? "" : comment))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationCommandClient::reviewActionResult);
    }

    @Override
    public CompletableFuture<ReviewActionView> deleteReview(UUID islandId, UUID reviewerUuid) {
        requireId(islandId, "islandId");
        requireId(reviewerUuid, "reviewerUuid");
        return core.postResultBody("/v1/islands/reviews/delete", CoreJsonPayload.object("islandId", islandId, "reviewerUuid", reviewerUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationCommandClient::reviewActionResult);
    }

    @Override
    public CompletableFuture<ReviewActionView> reportReview(UUID islandId, UUID reviewerUuid, UUID reporterUuid, String reason) {
        requireId(islandId, "islandId");
        requireId(reviewerUuid, "reviewerUuid");
        requireId(reporterUuid, "reporterUuid");
        return core.postResultBody("/v1/islands/reviews/report", CoreJsonPayload.object(
                "islandId", islandId,
                "reviewerUuid", reviewerUuid,
                "reporterUuid", reporterUuid,
                "reason", reason == null ? "" : reason))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationCommandClient::reviewReportActionResult);
    }

    @Override
    public CompletableFuture<List<ReviewModerationView>> reviewModerationQueue(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        return core.postResultBody("/v1/admin/reviews/moderation", CoreJsonPayload.object("limit", cappedLimit))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationCommandClient::reviewModerationQueueResult);
    }

    @Override
    public CompletableFuture<ReviewModerationView> moderateReview(UUID islandId, UUID reviewerUuid, UUID moderatorUuid, String moderationState, String note) {
        requireId(islandId, "islandId");
        requireId(reviewerUuid, "reviewerUuid");
        requireId(moderatorUuid, "moderatorUuid");
        String state = requireModerationState(moderationState);
        return core.postResultBody("/v1/admin/reviews/moderate", CoreJsonPayload.object(
                "islandId", islandId,
                "reviewerUuid", reviewerUuid,
                "moderatorUuid", moderatorUuid,
                "moderationState", state,
                "note", note == null ? "" : note))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationCommandClient::reviewModerationActionResult);
    }

    static ReviewActionView reviewActionResult(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new ReviewActionView(CoreJson.accepted(root), CoreJson.text(root, "code"));
    }

    static ReviewActionView reviewReportActionResult(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> moderation = CoreJson.objectValue(root, "moderation");
        long reportCount = Math.max(0L, CoreJson.number(moderation, "reportCount"));
        return new ReviewActionView(
            CoreJson.accepted(root),
            CoreJson.code(root, "REVIEW_REPORTED"),
            CoreJson.text(moderation, "moderationState"),
            (int) Math.min(Integer.MAX_VALUE, reportCount)
        );
    }

    static List<ReviewModerationView> reviewModerationQueueResult(String body) {
        return CoreJson.entries(body, "reviews").stream()
            .map(JdkNavigationCommandClient::reviewModerationView)
            .toList();
    }

    static ReviewModerationView reviewModerationActionResult(String body) {
        return reviewModerationView(CoreJson.objectValue(CoreJson.object(body), "moderation"));
    }

    private static ReviewModerationView reviewModerationView(Map<?, ?> values) {
        long reportCount = Math.max(0L, CoreJson.number(values, "reportCount"));
        return new ReviewModerationView(
            CoreJson.text(values, "islandId"),
            CoreJson.text(values, "islandName"),
            CoreJson.text(values, "reviewerUuid"),
            CoreJson.text(values, "reviewerName"),
            CoreJson.text(values, "moderationState"),
            (int) Math.min(Integer.MAX_VALUE, reportCount),
            CoreJson.text(values, "reportReason"),
            CoreJson.text(values, "moderatedBy"),
            CoreJson.text(values, "moderatedAt"),
            CoreJson.text(values, "moderationNote"),
            CoreJson.text(values, "updatedAt")
        );
    }

    private static String requireModerationState(String value) {
        String state = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!state.equals("VISIBLE") && !state.equals("REPORTED") && !state.equals("HIDDEN")) {
            throw new IllegalArgumentException("moderationState must be VISIBLE, REPORTED, or HIDDEN");
        }
        return state;
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
