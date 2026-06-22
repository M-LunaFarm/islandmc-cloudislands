package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.coreclient.NavigationCommandClient;
import kr.lunaf.cloudislands.coreclient.NavigationQueryClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PublicIslandView;

public final class IslandNavigationUseCase {
    private final CoreApiClient coreApiClient;
    private final NavigationQueryClient navigationQueries;
    private final NavigationCommandClient navigationCommands;

    public IslandNavigationUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.navigationQueries = coreApiClient.navigation();
        this.navigationCommands = coreApiClient.navigationCommands();
    }

    IslandNavigationUseCase(CoreApiClient coreApiClient, NavigationQueryClient navigationQueries) {
        this(coreApiClient, navigationQueries, coreApiClient.navigationCommands());
    }

    IslandNavigationUseCase(CoreApiClient coreApiClient, NavigationQueryClient navigationQueries, NavigationCommandClient navigationCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (navigationQueries == null) {
            throw new IllegalArgumentException("navigationQueries is required");
        }
        if (navigationCommands == null) {
            throw new IllegalArgumentException("navigationCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.navigationQueries = navigationQueries;
        this.navigationCommands = navigationCommands;
    }

    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID islandId, MutationRunner runner) {
        requirePlayer(visitorUuid);
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        requireRunner(runner);
        return runner.mutate("route.ticket.visit", () -> navigationCommands.createVisitTicket(visitorUuid, islandId));
    }

    public CompletableFuture<RouteTicket> createVisitTicketByName(UUID visitorUuid, String islandName, MutationRunner runner) {
        requirePlayer(visitorUuid);
        String normalizedIslandName = requireText(islandName, "islandName");
        requireRunner(runner);
        return runner.mutate("route.ticket.visit.name", () -> navigationCommands.createVisitTicket(visitorUuid, normalizedIslandName));
    }

    public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid, MutationRunner runner) {
        requirePlayer(visitorUuid);
        if (ownerUuid == null) {
            throw new IllegalArgumentException("ownerUuid is required");
        }
        requireRunner(runner);
        return runner.mutate("route.ticket.visit.owner", () -> navigationCommands.createVisitTicketForOwner(visitorUuid, ownerUuid));
    }

    public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid, MutationRunner runner) {
        requirePlayer(visitorUuid);
        requireRunner(runner);
        return runner.mutate("route.ticket.random-visit", () -> navigationCommands.createRandomVisitTicket(visitorUuid));
    }

    public CompletableFuture<RouteTicket> resolveVisitTarget(UUID visitorUuid, String target, MutationRunner runner) {
        requirePlayer(visitorUuid);
        String normalizedTarget = requireText(target, "target");
        requireRunner(runner);
        UUID islandId = uuid(normalizedTarget);
        if (islandId != null) {
            return createVisitTicket(visitorUuid, islandId, runner);
        }
        return navigationQueries.playerProfileByName(normalizedTarget)
            .thenCompose(profile -> {
                UUID primaryIslandId = uuid(profile.primaryIslandId());
                if (primaryIslandId == null) {
                    return createVisitTicketByName(visitorUuid, normalizedTarget, runner);
                }
                UUID ownerUuid = uuid(profile.playerUuid());
                return ownerUuid == null
                    ? createVisitTicket(visitorUuid, primaryIslandId, runner)
                    : createVisitTicketForOwner(visitorUuid, ownerUuid, runner);
            })
            .exceptionallyCompose(_error -> createVisitTicketByName(visitorUuid, normalizedTarget, runner));
    }

    public CompletableFuture<List<PublicIslandView>> publicIslandViews(int limit) {
        return navigationQueries.publicIslands(limit).thenApply(views -> views.stream().map(IslandNavigationUseCase::publicIslandView).toList());
    }

    public CompletableFuture<ReviewListView> reviewViews(UUID islandId, int limit) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return navigationQueries.listReviews(islandId, limit).thenApply(IslandNavigationUseCase::reviewViews);
    }

    public CompletableFuture<IslandVisitorStatsView> visitorStats(UUID islandId, int recentLimit) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return coreApiClient.visitorStats().stats(islandId, boundedLimit(recentLimit));
    }

    private CompletableFuture<ReviewActionResult> setReviewResult(UUID islandId, UUID reviewerUuid, int rating, String comment, IdempotentMutationRunner runner) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        requirePlayer(reviewerUuid);
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.review.set", () -> navigationCommands.setReview(islandId, reviewerUuid, rating, comment))
            .thenApply(result -> new ReviewActionResult(result.accepted(), result.code()));
    }

    public CompletableFuture<ReviewActionResult> setReviewAction(UUID islandId, UUID reviewerUuid, int rating, String comment, IdempotentMutationRunner runner) {
        return setReviewResult(islandId, reviewerUuid, rating, comment, runner);
    }

    public CompletableFuture<ReviewActionResult> deleteReviewAction(UUID islandId, UUID reviewerUuid, IdempotentMutationRunner runner) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        requirePlayer(reviewerUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.review.delete", () -> navigationCommands.deleteReview(islandId, reviewerUuid))
            .thenApply(result -> new ReviewActionResult(result.accepted(), result.code()));
    }

    private static PublicIslandView publicIslandView(CoreGuiViews.PublicIslandView view) {
        return new PublicIslandView(view.islandId(), view.ownerUuid(), view.name(), view.level(), view.worth());
    }

    private static ReviewListView reviewViews(kr.lunaf.cloudislands.coreclient.ReviewListView view) {
        return new ReviewListView(
            view.count(),
            view.average(),
            view.reviews().stream()
                .map(review -> new ReviewView(review.reviewerUuid(), review.rating(), review.comment()))
                .toList()
        );
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static void requirePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static void requireIdempotentRunner(IdempotentMutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record ReviewListView(long count, double average, List<ReviewView> reviews) {
        public ReviewListView {
            reviews = reviews == null ? List.of() : List.copyOf(reviews);
        }
    }

    public record ReviewView(String reviewerUuid, long rating, String comment) {
        public ReviewView {
            reviewerUuid = reviewerUuid == null ? "" : reviewerUuid;
            comment = comment == null ? "" : comment;
        }
    }

    public record ReviewActionResult(boolean accepted, String code) {
        public ReviewActionResult {
            code = code == null ? "" : code;
        }
    }
}
