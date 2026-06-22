package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdkNavigationQueryClient implements NavigationQueryClient {
    private final JdkCoreApiClient core;

    public JdkNavigationQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName) {
        String normalizedPlayerName = requireText(playerName, "playerName");
        return core.playerProfiles().findByName(normalizedPlayerName).thenApply(CorePlayerProfileJson::guiProfile);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.PlayerIslandView>> playerIslands(UUID playerUuid) {
        requirePlayer(playerUuid);
        return core.postBody("/v1/players/islands", CoreJsonPayload.object("playerUuid", playerUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationQueryClient::playerIslandViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.PublicIslandView>> publicIslands(int limit) {
        return core.postBody("/v1/islands/public", CoreJsonPayload.object("limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationQueryClient::publicIslandViews);
    }

    @Override
    public CompletableFuture<ReviewListView> listReviews(UUID islandId, int limit) {
        requireIsland(islandId);
        return core.postBody("/v1/islands/reviews", CoreJsonPayload.object("islandId", islandId, "limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkNavigationQueryClient::reviewViews);
    }

    static ReviewListView reviewViews(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> summary = CoreJson.objectValue(root, "summary");
        List<ReviewView> reviews = CoreJson.objects(root, "reviews").stream()
            .map(review -> new ReviewView(
                CoreJson.text(review, "islandId"),
                CoreJson.text(review, "reviewerUuid"),
                CoreJson.number(review, "rating"),
                CoreJson.text(review, "comment"),
                CoreJson.text(review, "createdAt"),
                CoreJson.text(review, "updatedAt")
            ))
            .filter(review -> !review.reviewerUuid().isBlank())
            .toList();
        return new ReviewListView(CoreJson.number(summary, "count"), CoreJson.decimal(summary, "average"), reviews);
    }

    static List<CoreGuiViews.PlayerIslandView> playerIslandViews(String body) {
        return CoreJson.entries(body).stream()
            .map(JdkNavigationQueryClient::playerIslandView)
            .filter(view -> !view.islandId().isBlank())
            .toList();
    }

    static List<CoreGuiViews.PublicIslandView> publicIslandViews(String body) {
        return CoreJson.entries(body).stream()
            .map(JdkNavigationQueryClient::publicIslandView)
            .filter(view -> !view.islandId().isBlank())
            .toList();
    }

    private static CoreGuiViews.PlayerIslandView playerIslandView(Map<?, ?> object) {
        String islandId = CoreJson.text(object, "islandId");
        String name = CoreJson.text(object, "name");
        String role = CoreJson.text(object, "role");
        return new CoreGuiViews.PlayerIslandView(
            islandId,
            name.isBlank() ? islandId : name,
            CoreJson.text(object, "state"),
            role.isBlank() ? "MEMBER" : role,
            CoreJson.number(object, "level"),
            CoreJson.text(object, "worth")
        );
    }

    private static CoreGuiViews.PublicIslandView publicIslandView(Map<?, ?> object) {
        String islandId = CoreJson.text(object, "islandId");
        String name = CoreJson.text(object, "name");
        return new CoreGuiViews.PublicIslandView(
            islandId,
            CoreJson.text(object, "ownerUuid"),
            name.isBlank() ? islandId : name,
            CoreJson.number(object, "level"),
            CoreJson.text(object, "worth")
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

}
