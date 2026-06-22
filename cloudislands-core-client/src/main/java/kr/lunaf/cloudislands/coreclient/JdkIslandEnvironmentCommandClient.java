package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;

public final class JdkIslandEnvironmentCommandClient implements IslandEnvironmentCommandClient {
    private final JdkCoreApiClient core;

    public JdkIslandEnvironmentCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<EnvironmentActionView> setBiome(UUID islandId, UUID actorUuid, String biomeKey) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/biome/set", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "biomeKey", biomeKey == null ? "" : biomeKey))
            .thenApply(body -> actionResult(body, "BIOME_SET"));
    }

    @Override
    public CompletableFuture<EnvironmentActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireFlag(flag);
        return core.postWithResultBody("/v1/islands/flags/set", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "flag", flag.name(), "value", value == null ? "" : value))
            .thenApply(body -> actionResult(body, "FLAG_SET"));
    }

    @Override
    public CompletableFuture<EnvironmentActionView> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.post("/v1/islands/limits/set", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "limitKey", limitKey == null ? "" : limitKey, "value", value))
            .thenApply(body -> actionResult(body, "LIMIT_SET"));
    }

    private static EnvironmentActionView actionResult(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        return new EnvironmentActionView(
            CoreJson.accepted(root),
            CoreJson.code(root, successCode),
            CoreJson.firstText(root, "limitKey", "biomeKey", "flag", "key"),
            CoreJson.number(root, "value"),
            CoreJson.text(root, "islandId"),
            CoreJson.text(root, "updatedBy"),
            CoreJson.text(root, "updatedAt")
        );
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requireFlag(IslandFlag flag) {
        if (flag == null) {
            throw new IllegalArgumentException("flag is required");
        }
    }
}
