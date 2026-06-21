package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreNavigationQueryClient implements NavigationQueryClient {
    private final CoreApiClient delegate;

    public CoreNavigationQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName) {
        String normalizedPlayerName = requireText(playerName, "playerName");
        return delegate.playerInfoByName(normalizedPlayerName).thenApply(CoreGuiViews::playerProfile);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.PublicIslandView>> publicIslands(int limit) {
        return CoreGuiViews.publicIslands(delegate, boundedLimit(limit));
    }

    @Override
    public CompletableFuture<ReviewListView> listReviews(UUID islandId, int limit) {
        requireIsland(islandId);
        return delegate.listIslandReviews(islandId, boundedLimit(limit)).thenApply(CoreNavigationQueryClient::reviewViews);
    }

    private static ReviewListView reviewViews(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        Map<?, ?> summary = SimpleJson.object(root.get("summary"));
        List<ReviewView> reviews = SimpleJson.list(root.get("reviews")).stream()
            .map(SimpleJson::object)
            .map(review -> new ReviewView(
                text(review, "islandId"),
                text(review, "reviewerUuid"),
                SimpleJson.number(review.get("rating")),
                text(review, "comment"),
                text(review, "createdAt"),
                text(review, "updatedAt")
            ))
            .filter(review -> !review.reviewerUuid().isBlank())
            .toList();
        return new ReviewListView(SimpleJson.number(summary.get("count")), doubleValue(summary.get("average")), reviews);
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }
}
