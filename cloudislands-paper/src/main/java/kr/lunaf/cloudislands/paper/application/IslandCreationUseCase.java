package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandCreationUseCase {
    private final CoreApiClient coreApiClient;

    public IslandCreationUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<CreateIslandResult> create(UUID playerUuid, String templateId, MutationRunner runner) {
        requirePlayer(playerUuid);
        requireRunner(runner);
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        return runner.mutate("island.create", () -> coreApiClient.createIsland(playerUuid, normalizedTemplateId));
    }

    public CompletableFuture<DeleteIslandResult> delete(UUID playerUuid, UUID islandId, IdempotentMutationRunner runner) {
        requirePlayer(playerUuid);
        requireIsland(islandId);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.delete", () -> coreApiClient.deleteIsland(playerUuid, islandId));
    }

    public CompletableFuture<String> reset(UUID islandId, UUID actorUuid, String reason, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requirePlayer(actorUuid);
        requireIdempotentRunner(runner);
        String normalizedReason = reason == null || reason.isBlank() ? "player-reset" : reason.trim();
        return runner.mutateIdempotent("island.reset", () -> coreApiClient.resetIslandResult(islandId, actorUuid, normalizedReason));
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
}
