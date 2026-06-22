package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        return core.postResultBody("/v1/admin/islands/info", CoreJsonPayload.object("lookupUuid", lookupUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreGuiViews::islandInfoView);
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
        return core.postResultBody("/v1/admin/islands/where", CoreJsonPayload.object("islandId", islandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminIslandQueryClient::runtime);
    }

    static AdminIslandRuntimeView runtime(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new AdminIslandRuntimeView(
            CoreJson.text(root, "islandId"),
            CoreJson.text(root, "state"),
            nullableText(root, "activeNode"),
            nullableText(root, "activeWorld"),
            nullableNumber(root, "cellX"),
            nullableNumber(root, "cellZ"),
            nullableText(root, "leaseOwner"),
            CoreJson.number(root, "fencingToken"),
            nullableText(root, "activatedAt"),
            nullableText(root, "lastHeartbeat"),
            CoreJson.text(root, "code")
        );
    }

    private static String nullableText(Map<?, ?> object, String key) {
        if (!object.containsKey(key) || object.get(key) == null) {
            return null;
        }
        return CoreJson.text(object, key);
    }

    private static Long nullableNumber(Map<?, ?> object, String key) {
        return !object.containsKey(key) || object.get(key) == null ? null : CoreJson.number(object, key);
    }
}
