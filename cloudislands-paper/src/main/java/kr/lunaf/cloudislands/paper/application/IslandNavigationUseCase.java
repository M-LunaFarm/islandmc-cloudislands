package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandNavigationUseCase {
    private final CoreApiClient coreApiClient;

    public IslandNavigationUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID islandId, MutationRunner runner) {
        requirePlayer(visitorUuid);
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        requireRunner(runner);
        return runner.mutate("route.ticket.visit", () -> coreApiClient.createVisitTicket(visitorUuid, islandId));
    }

    public CompletableFuture<RouteTicket> createVisitTicketByName(UUID visitorUuid, String islandName, MutationRunner runner) {
        requirePlayer(visitorUuid);
        String normalizedIslandName = requireText(islandName, "islandName");
        requireRunner(runner);
        return runner.mutate("route.ticket.visit.name", () -> coreApiClient.createVisitTicket(visitorUuid, normalizedIslandName));
    }

    public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid, MutationRunner runner) {
        requirePlayer(visitorUuid);
        if (ownerUuid == null) {
            throw new IllegalArgumentException("ownerUuid is required");
        }
        requireRunner(runner);
        return runner.mutate("route.ticket.visit.owner", () -> coreApiClient.createVisitTicketForOwner(visitorUuid, ownerUuid));
    }

    public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid, MutationRunner runner) {
        requirePlayer(visitorUuid);
        requireRunner(runner);
        return runner.mutate("route.ticket.random-visit", () -> coreApiClient.createRandomVisitTicket(visitorUuid));
    }

    public CompletableFuture<RouteTicket> resolveVisitTarget(UUID visitorUuid, String target, MutationRunner runner) {
        requirePlayer(visitorUuid);
        String normalizedTarget = requireText(target, "target");
        requireRunner(runner);
        UUID islandId = uuid(normalizedTarget);
        if (islandId != null) {
            return createVisitTicket(visitorUuid, islandId, runner);
        }
        return coreApiClient.playerInfoByName(normalizedTarget)
            .thenCompose(body -> {
                UUID primaryIslandId = uuid(text(body, "primaryIslandId"));
                if (primaryIslandId == null) {
                    return createVisitTicketByName(visitorUuid, normalizedTarget, runner);
                }
                UUID ownerUuid = uuid(text(body, "playerUuid"));
                return ownerUuid == null
                    ? createVisitTicket(visitorUuid, primaryIslandId, runner)
                    : createVisitTicketForOwner(visitorUuid, ownerUuid, runner);
            })
            .exceptionallyCompose(_error -> createVisitTicketByName(visitorUuid, normalizedTarget, runner));
    }

    public CompletableFuture<String> listPublicIslands(int limit) {
        return coreApiClient.listPublicIslands(boundedLimit(limit));
    }

    public CompletableFuture<String> listReviews(UUID islandId, int limit) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return coreApiClient.listIslandReviews(islandId, boundedLimit(limit));
    }

    public CompletableFuture<String> setReview(UUID islandId, UUID reviewerUuid, int rating, String comment, IdempotentMutationRunner runner) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        requirePlayer(reviewerUuid);
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.review.set", () -> coreApiClient.setIslandReview(islandId, reviewerUuid, rating, comment == null ? "" : comment));
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

    private static String text(String json, String key) {
        return SimpleJson.text(SimpleJson.object(SimpleJson.parse(json)).get(key));
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
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }
}
