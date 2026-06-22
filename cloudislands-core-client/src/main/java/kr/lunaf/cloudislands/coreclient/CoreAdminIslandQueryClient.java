package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreAdminIslandQueryClient implements AdminIslandQueryClient {
    private final CoreApiClient delegate;

    public CoreAdminIslandQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> info(UUID lookupUuid) {
        if (lookupUuid == null) {
            throw new IllegalArgumentException("lookupUuid is required");
        }
        return delegate.adminIslandInfo(lookupUuid).thenApply(CoreGuiViews::islandInfoView);
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> infoByName(String islandName) {
        String normalized = islandName == null ? "" : islandName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("islandName is required");
        }
        return delegate.islandInfoByName(normalized).thenApply(CoreGuiViews::islandInfoView);
    }

    @Override
    public CompletableFuture<AdminIslandRuntimeView> runtime(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return delegate.adminIslandWhere(islandId).thenApply(CoreAdminIslandQueryClient::runtime);
    }

    static AdminIslandRuntimeView runtime(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new AdminIslandRuntimeView(
            text(root, "islandId"),
            text(root, "state"),
            nullableText(root, "activeNode"),
            nullableText(root, "activeWorld"),
            nullableNumber(root.get("cellX")),
            nullableNumber(root.get("cellZ")),
            nullableText(root, "leaseOwner"),
            number(root, "fencingToken"),
            nullableText(root, "activatedAt"),
            nullableText(root, "lastHeartbeat"),
            text(root, "code")
        );
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static String nullableText(Map<?, ?> object, String key) {
        if (!object.containsKey(key) || object.get(key) == null) {
            return null;
        }
        return SimpleJson.text(object.get(key));
    }

    private static long number(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
    }

    private static Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
