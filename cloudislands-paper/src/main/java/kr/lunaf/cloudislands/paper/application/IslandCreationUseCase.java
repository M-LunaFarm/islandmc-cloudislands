package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.IslandLifecycleCommandClient;

public final class IslandCreationUseCase {
    private final CoreApiClient coreApiClient;
    private final IslandLifecycleCommandClient lifecycleCommands;

    public IslandCreationUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.lifecycleCommands = coreApiClient.lifecycle();
    }

    IslandCreationUseCase(CoreApiClient coreApiClient, IslandLifecycleCommandClient lifecycleCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (lifecycleCommands == null) {
            throw new IllegalArgumentException("lifecycleCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.lifecycleCommands = lifecycleCommands;
    }

    public CompletableFuture<CreateIslandResult> create(UUID playerUuid, String templateId, MutationRunner runner) {
        requirePlayer(playerUuid);
        requireRunner(runner);
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        return runner.mutate("island.create", () -> lifecycleCommands.createIsland(playerUuid, normalizedTemplateId));
    }

    public CompletableFuture<DeleteIslandResult> delete(UUID playerUuid, UUID islandId, IdempotentMutationRunner runner) {
        requirePlayer(playerUuid);
        requireIsland(islandId);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.delete", () -> lifecycleCommands.deleteIsland(playerUuid, islandId));
    }

    private CompletableFuture<IslandActionResult> resetResult(UUID islandId, UUID actorUuid, String reason, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requirePlayer(actorUuid);
        requireIdempotentRunner(runner);
        String normalizedReason = reason == null || reason.isBlank() ? "player-reset" : reason.trim();
        return runner.mutateIdempotent("island.reset", () -> lifecycleCommands.resetIsland(islandId, actorUuid, normalizedReason))
            .thenApply(result -> new IslandActionResult(result.accepted(), result.code()));
    }

    public CompletableFuture<IslandActionResult> resetAction(UUID islandId, UUID actorUuid, String reason, IdempotentMutationRunner runner) {
        return resetResult(islandId, actorUuid, reason, runner);
    }

    private static void requirePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid is required");
        }
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static void requireIdempotentRunner(IdempotentMutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record IslandActionResult(boolean accepted, String code) {
        public IslandActionResult {
            code = code == null ? "" : code;
        }
    }
}
