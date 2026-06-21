package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class SnapshotUseCase {
    private final CoreApiClient coreApiClient;

    public SnapshotUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> listSnapshots(UUID islandId, int limit) {
        requireIsland(islandId);
        return coreApiClient.listIslandSnapshots(islandId, boundedLimit(limit));
    }

    public CompletableFuture<String> requestSnapshot(UUID islandId, String reason, MutationRunner runner) {
        requireIsland(islandId);
        requireRunner(runner);
        String normalizedReason = normalizeReason(reason);
        return runner.mutate("island.snapshot.create", () -> coreApiClient.requestIslandSnapshotResult(islandId, normalizedReason));
    }

    public CompletableFuture<String> restoreSnapshot(UUID islandId, long snapshotNo, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireSnapshotNo(snapshotNo);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.snapshot.restore", () -> coreApiClient.restoreIslandSnapshotResult(islandId, snapshotNo));
    }

    public static long positiveSnapshotNo(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0L ? parsed : 0L;
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 20));
    }

    public static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "manual";
        }
        return reason.trim();
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireSnapshotNo(long snapshotNo) {
        if (snapshotNo <= 0L) {
            throw new IllegalArgumentException("positive snapshotNo is required");
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
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }
}
