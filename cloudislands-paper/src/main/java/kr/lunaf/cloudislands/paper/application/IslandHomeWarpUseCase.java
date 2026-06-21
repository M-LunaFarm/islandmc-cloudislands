package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandHomeWarpUseCase {
    private final CoreApiClient coreApiClient;

    public IslandHomeWarpUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireLocation(location);
        requireRunner(runner);
        return runner.mutate("island.home.set", () -> coreApiClient.setIslandHomeResult(islandId, actorUuid, normalizeName(name), location));
    }

    public CompletableFuture<String> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireLocation(location);
        requireRunner(runner);
        return runner.mutate("island.warp.set", () -> coreApiClient.setIslandWarpResult(islandId, actorUuid, normalizeName(name), location, publicAccess));
    }

    public CompletableFuture<String> listHomes(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandHomes(islandId);
    }

    public CompletableFuture<String> listWarps(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandWarps(islandId);
    }

    public CompletableFuture<String> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.islandInfo(islandId);
    }

    public CompletableFuture<String> deleteWarp(UUID islandId, UUID actorUuid, String name, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.warp.delete", () -> coreApiClient.deleteIslandWarpResult(islandId, actorUuid, normalizeName(name)));
    }

    public CompletableFuture<String> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.warp.public-access.set", () -> coreApiClient.setIslandWarpPublicAccessResult(islandId, actorUuid, normalizeName(name), publicAccess));
    }

    public CompletableFuture<String> listPublicWarps(int limit, String category, String query) {
        return coreApiClient.listPublicWarps(Math.max(1, Math.min(limit, 100)), category == null ? "" : category, query == null ? "" : query);
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

    private static void requireLocation(IslandLocation location) {
        if (location == null) {
            throw new IllegalArgumentException("location is required");
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

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? "default" : name;
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }
}
