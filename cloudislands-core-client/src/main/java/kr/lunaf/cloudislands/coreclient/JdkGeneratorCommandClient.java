package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;

public final class JdkGeneratorCommandClient implements GeneratorCommandClient {
    private final JdkCoreApiClient core;

    public JdkGeneratorCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<IslandGeneratorSnapshot> adminSetGenerator(UUID islandId, String generatorKey, int level) {
        requireIsland(islandId);
        return core.postResultBody("/v1/admin/islands/generator/set", CoreJsonPayload.object(
                "islandId", islandId,
                "generatorKey", generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey,
                "level", Math.max(1, level)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkGeneratorQueryClient::generatorView);
    }

    @Override
    public CompletableFuture<IslandGeneratorSnapshot> adminAddGeneratorLevels(UUID islandId, String generatorKey, int levels) {
        requireIsland(islandId);
        return core.postResultBody("/v1/admin/islands/generator/add", CoreJsonPayload.object(
                "islandId", islandId,
                "generatorKey", generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey,
                "levels", Math.max(1, levels)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkGeneratorQueryClient::generatorView);
    }

    @Override
    public CompletableFuture<IslandGeneratorSnapshot> adminClearGenerator(UUID islandId) {
        requireIsland(islandId);
        return core.postResultBody("/v1/admin/islands/generator/clear", CoreJsonPayload.object("islandId", islandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkGeneratorQueryClient::generatorView);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
