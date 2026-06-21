package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
