package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandEnvironmentUseCase {
    private final CoreApiClient coreApiClient;

    public IslandEnvironmentUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> islandBiome(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.islandBiome(islandId);
    }

    public CompletableFuture<String> setBiome(UUID islandId, UUID actorUuid, String biomeKey, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.biome.set", () -> coreApiClient.setIslandBiomeResult(islandId, actorUuid, biomeKey == null ? "" : biomeKey));
    }

    public CompletableFuture<String> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.islandInfo(islandId);
    }

    public CompletableFuture<String> listFlags(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandFlags(islandId);
    }

    public CompletableFuture<String> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireFlag(flag);
        requireRunner(runner);
        return runner.mutate("island.flag.set", () -> coreApiClient.setIslandFlagResult(islandId, actorUuid, flag, value == null ? "" : value));
    }

    public CompletableFuture<String> listLimits(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandLimits(islandId);
    }

    public CompletableFuture<String> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.limit.set", () -> coreApiClient.setIslandLimit(islandId, actorUuid, limitKey == null ? "" : limitKey, value));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireActor(UUID actorUuid) {
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
    }

    private static void requireFlag(IslandFlag flag) {
        if (flag == null) {
            throw new IllegalArgumentException("flag is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }
}
