package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreSnapshotQueryClient;
import kr.lunaf.cloudislands.coreclient.SnapshotQueryClient;

public final class SnapshotUseCase {
    private final CoreApiClient coreApiClient;
    private final SnapshotQueryClient snapshotQueries;

    public SnapshotUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.snapshotQueries = new CoreSnapshotQueryClient(coreApiClient);
    }

    SnapshotUseCase(CoreApiClient coreApiClient, SnapshotQueryClient snapshotQueries) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (snapshotQueries == null) {
            throw new IllegalArgumentException("snapshotQueries is required");
        }
        this.coreApiClient = coreApiClient;
        this.snapshotQueries = snapshotQueries;
    }

    public CompletableFuture<List<SnapshotView>> snapshotViews(UUID islandId, int limit) {
        requireIsland(islandId);
        return snapshotQueries.listSnapshots(islandId, boundedLimit(limit))
            .thenApply(snapshots -> snapshots.stream()
                .map(snapshot -> new SnapshotView(snapshot.snapshotNo(), snapshot.reason(), snapshot.sizeBytes(), snapshot.checksum()))
                .toList());
    }

    private CompletableFuture<String> requestSnapshotBody(UUID islandId, String reason, MutationRunner runner) {
        requireIsland(islandId);
        requireRunner(runner);
        String normalizedReason = normalizeReason(reason);
        return runner.mutate("island.snapshot.create", () -> coreApiClient.requestIslandSnapshotResult(islandId, normalizedReason));
    }

    public CompletableFuture<SnapshotActionResult> requestSnapshotAction(UUID islandId, String reason, MutationRunner runner) {
        return requestSnapshotBody(islandId, reason, runner)
            .thenApply(body -> snapshotAction(body, "SNAPSHOT_REQUESTED"));
    }

    private CompletableFuture<String> restoreSnapshotBody(UUID islandId, long snapshotNo, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireSnapshotNo(snapshotNo);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.snapshot.restore", () -> coreApiClient.restoreIslandSnapshotResult(islandId, snapshotNo));
    }

    public CompletableFuture<SnapshotActionResult> restoreSnapshotAction(UUID islandId, long snapshotNo, IdempotentMutationRunner runner) {
        return restoreSnapshotBody(islandId, snapshotNo, runner)
            .thenApply(body -> snapshotAction(body, "RESTORE_REQUESTED"));
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

    private static SnapshotActionResult snapshotAction(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = bool(root, "accepted", true);
        accepted = accepted && !root.containsKey("error") && !Boolean.FALSE.equals(root.get("applied"));
        String code = text(root, "code");
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new SnapshotActionResult(accepted, code);
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
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
