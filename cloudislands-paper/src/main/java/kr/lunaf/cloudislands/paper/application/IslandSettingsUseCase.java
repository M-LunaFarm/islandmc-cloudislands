package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandSettingsUseCase {
    private final CoreApiClient coreApiClient;

    public IslandSettingsUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.public-access.set", () -> coreApiClient.setIslandPublicAccessResult(islandId, actorUuid, publicAccess));
    }

    public CompletableFuture<String> setLocked(UUID islandId, UUID actorUuid, boolean locked, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.locked.set", () -> coreApiClient.setIslandLockedResult(islandId, actorUuid, locked));
    }

    public CompletableFuture<String> setName(UUID islandId, UUID actorUuid, String name, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.name.set", () -> coreApiClient.setIslandNameResult(islandId, actorUuid, name == null ? "" : name));
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
