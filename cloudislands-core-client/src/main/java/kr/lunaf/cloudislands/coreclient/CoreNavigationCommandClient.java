package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreNavigationCommandClient implements NavigationCommandClient {
    private final CoreApiClient delegate;

    public CoreNavigationCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) {
        requireId(playerUuid, "playerUuid");
        return delegate.createHomeTicket(playerUuid, homeName == null || homeName.isBlank() ? "default" : homeName.trim());
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID islandId) {
        requireId(visitorUuid, "visitorUuid");
        requireId(islandId, "islandId");
        return delegate.createVisitTicket(visitorUuid, islandId);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) {
        requireId(visitorUuid, "visitorUuid");
        if (islandName == null || islandName.isBlank()) {
            throw new IllegalArgumentException("islandName is required");
        }
        return delegate.createVisitTicket(visitorUuid, islandName.trim());
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) {
        requireId(visitorUuid, "visitorUuid");
        requireId(ownerUuid, "ownerUuid");
        return delegate.createVisitTicketForOwner(visitorUuid, ownerUuid);
    }

    @Override
    public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) {
        requireId(visitorUuid, "visitorUuid");
        return delegate.createRandomVisitTicket(visitorUuid);
    }

    @Override
    public CompletableFuture<ReviewActionView> setReview(UUID islandId, UUID reviewerUuid, int rating, String comment) {
        requireId(islandId, "islandId");
        requireId(reviewerUuid, "reviewerUuid");
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        return delegate.setIslandReview(islandId, reviewerUuid, rating, comment == null ? "" : comment)
            .thenApply(CoreNavigationCommandClient::reviewActionResult);
    }

    private static ReviewActionView reviewActionResult(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        return new ReviewActionView(accepted, code);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
