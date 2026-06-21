package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.SnapshotActionView;
import kr.lunaf.cloudislands.coreclient.SnapshotCommandClient;
import kr.lunaf.cloudislands.coreclient.SnapshotQueryClient;

public final class SnapshotUseCase {
    private final CoreApiClient coreApiClient;
    private final SnapshotQueryClient snapshotQueries;
    private final SnapshotCommandClient snapshotCommands;

    public SnapshotUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.snapshotQueries = coreApiClient.snapshots();
        this.snapshotCommands = coreApiClient.snapshotCommands();
    }

    SnapshotUseCase(CoreApiClient coreApiClient, SnapshotQueryClient snapshotQueries) {
        this(coreApiClient, snapshotQueries, coreApiClient.snapshotCommands());
    }

    SnapshotUseCase(CoreApiClient coreApiClient, SnapshotQueryClient snapshotQueries, SnapshotCommandClient snapshotCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (snapshotQueries == null) {
            throw new IllegalArgumentException("snapshotQueries is required");
        }
        if (snapshotCommands == null) {
            throw new IllegalArgumentException("snapshotCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.snapshotQueries = snapshotQueries;
        this.snapshotCommands = snapshotCommands;
    }

    public CompletableFuture<List<SnapshotView>> snapshotViews(UUID islandId, int limit) {
        requireIsland(islandId);
        return snapshotQueries.listSnapshots(islandId, boundedLimit(limit))
            .thenApply(snapshots -> snapshots.stream()
                .map(snapshot -> new SnapshotView(snapshot.snapshotNo(), snapshot.reason(), snapshot.sizeBytes(), snapshot.checksum()))
                .toList());
    }

    private CompletableFuture<SnapshotActionView> requestSnapshotBody(UUID islandId, String reason, MutationRunner runner) {
        requireIsland(islandId);
        requireRunner(runner);
        String normalizedReason = normalizeReason(reason);
        return runner.mutate("island.snapshot.create", () -> snapshotCommands.requestSnapshot(islandId, normalizedReason));
    }

    public CompletableFuture<SnapshotActionResult> requestSnapshotAction(UUID islandId, String reason, MutationRunner runner) {
        return requestSnapshotBody(islandId, reason, runner)
            .thenApply(SnapshotUseCase::snapshotAction);
    }

    private CompletableFuture<SnapshotActionView> restoreSnapshotBody(UUID islandId, long snapshotNo, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireSnapshotNo(snapshotNo);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.snapshot.restore", () -> snapshotCommands.restoreSnapshot(islandId, snapshotNo));
    }

    public CompletableFuture<SnapshotActionResult> restoreSnapshotAction(UUID islandId, long snapshotNo, IdempotentMutationRunner runner) {
        return restoreSnapshotBody(islandId, snapshotNo, runner)
            .thenApply(SnapshotUseCase::snapshotAction);
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

    private static SnapshotActionResult snapshotAction(SnapshotActionView view) {
        return new SnapshotActionResult(view.accepted(), view.code());
    }

    @FunctionalInterface
    public interface MutationRunner {
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record SnapshotView(long snapshotNo, String reason, long sizeBytes, String checksum) {
        public SnapshotView {
            reason = reason == null ? "" : reason;
            checksum = checksum == null ? "" : checksum;
        }
    }

    public record SnapshotActionResult(boolean accepted, String code) {
        public SnapshotActionResult {
            code = code == null ? "" : code;
        }
    }
}
