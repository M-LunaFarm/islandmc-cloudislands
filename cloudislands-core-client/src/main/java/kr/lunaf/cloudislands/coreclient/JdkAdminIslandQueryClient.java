package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class JdkAdminIslandQueryClient implements AdminIslandQueryClient {
    private final JdkCoreApiClient core;

    JdkAdminIslandQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> info(UUID lookupUuid) {
        if (lookupUuid == null) {
            throw new IllegalArgumentException("lookupUuid is required");
        }
        return core.postWithResultBody("/v1/admin/islands/info", CoreJsonPayload.object("lookupUuid", lookupUuid)).thenApply(CoreGuiViews::islandInfoView);
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> infoByName(String islandName) {
        String normalized = islandName == null ? "" : islandName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("islandName is required");
        }
        return core.islands().findIslandByName(normalized);
    }

    @Override
    public CompletableFuture<AdminIslandRuntimeView> runtime(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return core.postWithResultBody("/v1/admin/islands/where", CoreJsonPayload.object("islandId", islandId)).thenApply(JdkAdminIslandQueryClient::runtime);
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
