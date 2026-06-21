package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreIslandEnvironmentCommandClient;
import kr.lunaf.cloudislands.coreclient.EnvironmentActionView;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentCommandClient;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.IslandInfoView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LimitView;

public final class IslandEnvironmentUseCase {
    private final CoreApiClient coreApiClient;
    private final IslandEnvironmentQueryClient environmentQueries;
    private final IslandEnvironmentCommandClient environmentCommands;

    public IslandEnvironmentUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.environmentQueries = coreApiClient.environment();
        this.environmentCommands = new CoreIslandEnvironmentCommandClient(coreApiClient);
    }

    IslandEnvironmentUseCase(CoreApiClient coreApiClient, IslandEnvironmentQueryClient environmentQueries) {
        this(coreApiClient, environmentQueries, new CoreIslandEnvironmentCommandClient(coreApiClient));
    }

    IslandEnvironmentUseCase(CoreApiClient coreApiClient, IslandEnvironmentQueryClient environmentQueries, IslandEnvironmentCommandClient environmentCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (environmentQueries == null) {
            throw new IllegalArgumentException("environmentQueries is required");
        }
        if (environmentCommands == null) {
            throw new IllegalArgumentException("environmentCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.environmentQueries = environmentQueries;
        this.environmentCommands = environmentCommands;
    }

    public CompletableFuture<BiomeValue> islandBiomeValue(UUID islandId) {
        requireIsland(islandId);
        return environmentQueries.islandBiome(islandId).thenApply(biome -> new BiomeValue(biome.key()));
    }

    private CompletableFuture<EnvironmentActionView> setBiomeBody(UUID islandId, UUID actorUuid, String biomeKey, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.biome.set", () -> environmentCommands.setBiome(islandId, actorUuid, biomeKey == null ? "" : biomeKey));
    }

    public CompletableFuture<EnvironmentActionResult> setBiomeAction(UUID islandId, UUID actorUuid, String biomeKey, MutationRunner runner) {
        return setBiomeBody(islandId, actorUuid, biomeKey, runner)
            .thenApply(IslandEnvironmentUseCase::actionResult);
    }

    public CompletableFuture<IslandInfoView> islandInfoView(UUID islandId) {
        requireIsland(islandId);
        return environmentQueries.getIsland(islandId);
    }

    public CompletableFuture<Map<IslandFlag, String>> flagValues(UUID islandId) {
        requireIsland(islandId);
        return environmentQueries.flagValues(islandId);
    }

    private CompletableFuture<EnvironmentActionView> setFlagBody(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireFlag(flag);
        requireRunner(runner);
        return runner.mutate("island.flag.set", () -> environmentCommands.setFlag(islandId, actorUuid, flag, value == null ? "" : value));
    }

    public CompletableFuture<EnvironmentActionResult> setFlagAction(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        return setFlagBody(islandId, actorUuid, flag, value, runner)
            .thenApply(IslandEnvironmentUseCase::actionResult);
    }

    public CompletableFuture<List<LimitView>> limitViews(UUID islandId) {
        requireIsland(islandId);
        return environmentQueries.limitViews(islandId);
    }

    private CompletableFuture<EnvironmentActionView> setLimitBody(UUID islandId, UUID actorUuid, String limitKey, long value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.limit.set", () -> environmentCommands.setLimit(islandId, actorUuid, limitKey == null ? "" : limitKey, value));
    }

    public CompletableFuture<EnvironmentActionResult> setLimitAction(UUID islandId, UUID actorUuid, String limitKey, long value, MutationRunner runner) {
        return setLimitBody(islandId, actorUuid, limitKey, value, runner)
            .thenApply(IslandEnvironmentUseCase::actionResult);
    }

    private static EnvironmentActionResult actionResult(EnvironmentActionView view) {
        return new EnvironmentActionResult(view.accepted(), view.code(), view.key(), view.value());
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
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record EnvironmentActionResult(boolean accepted, String code, String key, long value) {
        public EnvironmentActionResult {
            code = code == null ? "" : code;
            key = key == null ? "" : key;
        }
    }

    public record BiomeValue(String key) {
        public BiomeValue {
            key = key == null ? "" : key;
        }
    }
}
