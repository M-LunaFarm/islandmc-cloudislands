package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandProgressionUseCase {
    private final CoreApiClient coreApiClient;

    public IslandProgressionUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.islandInfo(islandId);
    }

    public CompletableFuture<String> blockDetails(UUID islandId, int limit) {
        requireIsland(islandId);
        return coreApiClient.islandBlockDetails(islandId, boundedLimit(limit));
    }

    public CompletableFuture<String> topIslandsByWorth(int limit) {
        return coreApiClient.topIslandsByWorth(boundedLimit(limit));
    }

    public CompletableFuture<String> topIslandsByLevel(int limit) {
        return coreApiClient.topIslandsByLevel(boundedLimit(limit));
    }

    public CompletableFuture<String> topIslandsByReviews(int limit) {
        return coreApiClient.topIslandsByReviews(boundedLimit(limit));
    }

    public CompletableFuture<String> recalculateLevel(UUID islandId, UUID actorUuid) {
        requireIsland(islandId);
        requireActor(actorUuid);
        return coreApiClient.recalculateIslandLevel(islandId, actorUuid);
    }

    public CompletableFuture<String> listUpgrades(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandUpgrades(islandId);
    }

    public CompletableFuture<String> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.upgrade.purchase", () -> coreApiClient.purchaseIslandUpgrade(islandId, actorUuid, upgradeKey == null ? "" : upgradeKey));
    }

    public CompletableFuture<String> listMissions(UUID islandId, String kind) {
        requireIsland(islandId);
        return coreApiClient.listIslandMissions(islandId, normalizeKind(kind));
    }

    public CompletableFuture<String> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.mission.complete", () -> coreApiClient.completeIslandMission(islandId, actorUuid, missionKey == null ? "" : missionKey, normalizeKind(kind)));
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static String normalizeKind(String kind) {
        return kind == null || kind.isBlank() ? "MISSION" : kind;
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

    private static void requireIdempotentRunner(IdempotentMutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }
}
