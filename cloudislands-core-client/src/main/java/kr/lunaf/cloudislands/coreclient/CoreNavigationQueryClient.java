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
        return delegate.playerProfiles().findByName(normalizedPlayerName).thenApply(CorePlayerProfileJson::guiProfile);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.PlayerIslandView>> playerIslands(UUID playerUuid) {
        requirePlayer(playerUuid);
        return delegate.listPlayerIslands(playerUuid).thenApply(CoreNavigationQueryClient::playerIslandViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.PublicIslandView>> publicIslands(int limit) {
        return delegate.listPublicIslands(boundedLimit(limit)).thenApply(CoreNavigationQueryClient::publicIslandViews);
    }

    @Override
    public CompletableFuture<ReviewListView> listReviews(UUID islandId, int limit) {
        requireIsland(islandId);
        return delegate.listIslandReviews(islandId, boundedLimit(limit)).thenApply(CoreNavigationQueryClient::reviewViews);
    }

    static ReviewListView reviewViews(String body) {
        Map<?, ?> root = CoreJson.object(body);
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

    static List<CoreGuiViews.PlayerIslandView> playerIslandViews(String body) {
        return CoreJson.entries(body).stream()
            .map(CoreNavigationQueryClient::playerIslandView)
            .filter(view -> !view.islandId().isBlank())
            .toList();
    }

    static List<CoreGuiViews.PublicIslandView> publicIslandViews(String body) {
        return CoreJson.entries(body).stream()
            .map(CoreNavigationQueryClient::publicIslandView)
            .filter(view -> !view.islandId().isBlank())
            .toList();
    }

    private static CoreGuiViews.PlayerIslandView playerIslandView(Map<?, ?> object) {
        String islandId = text(object, "islandId");
        String name = text(object, "name");
        String role = text(object, "role");
        return new CoreGuiViews.PlayerIslandView(
            islandId,
            name.isBlank() ? islandId : name,
            text(object, "state"),
            role.isBlank() ? "MEMBER" : role,
            SimpleJson.number(object.get("level")),
            text(object, "worth")
        );
    }

    private static CoreGuiViews.PublicIslandView publicIslandView(Map<?, ?> object) {
        String islandId = text(object, "islandId");
        String name = text(object, "name");
        return new CoreGuiViews.PublicIslandView(
            islandId,
            text(object, "ownerUuid"),
            name.isBlank() ? islandId : name,
            SimpleJson.number(object.get("level")),
            text(object, "worth")
        );
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requirePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid is required");
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
