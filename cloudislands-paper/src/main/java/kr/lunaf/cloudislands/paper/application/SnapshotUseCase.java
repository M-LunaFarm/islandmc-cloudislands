package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class SnapshotUseCase {
    private final CoreApiClient coreApiClient;

    public SnapshotUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    private CompletableFuture<String> listSnapshotBodies(UUID islandId, int limit) {
        requireIsland(islandId);
        return coreApiClient.listIslandSnapshots(islandId, boundedLimit(limit));
    }

    public CompletableFuture<List<SnapshotView>> snapshotViews(UUID islandId, int limit) {
        return listSnapshotBodies(islandId, limit).thenApply(SnapshotUseCase::snapshotViews);
    }

    public CompletableFuture<String> requestSnapshot(UUID islandId, String reason, MutationRunner runner) {
        requireIsland(islandId);
        requireRunner(runner);
        String normalizedReason = normalizeReason(reason);
        return runner.mutate("island.snapshot.create", () -> coreApiClient.requestIslandSnapshotResult(islandId, normalizedReason));
    }

    public CompletableFuture<SnapshotActionResult> requestSnapshotAction(UUID islandId, String reason, MutationRunner runner) {
        return requestSnapshot(islandId, reason, runner)
            .thenApply(body -> snapshotAction(body, "SNAPSHOT_REQUESTED"));
    }

    public CompletableFuture<String> restoreSnapshot(UUID islandId, long snapshotNo, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireSnapshotNo(snapshotNo);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.snapshot.restore", () -> coreApiClient.restoreIslandSnapshotResult(islandId, snapshotNo));
    }

    public CompletableFuture<SnapshotActionResult> restoreSnapshotAction(UUID islandId, long snapshotNo, IdempotentMutationRunner runner) {
        return restoreSnapshot(islandId, snapshotNo, runner)
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

    private static List<SnapshotView> snapshotViews(String body) {
        return entries(body).stream()
            .map(object -> new SnapshotView(
                SimpleJson.number(object.get("snapshotNo")),
                text(object, "reason"),
                SimpleJson.number(object.get("sizeBytes")),
                text(object, "checksum")
            ))
            .filter(snapshot -> snapshot.snapshotNo() > 0L)
            .toList();
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

    private static List<Map<?, ?>> entries(String body) {
        Object parsed = SimpleJson.parse(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        for (Object value : root.values()) {
            if (value instanceof List<?>) {
                return SimpleJson.list(value).stream()
                    .map(SimpleJson::object)
                    .filter(map -> !map.isEmpty())
                    .toList();
            }
        }
        return root.isEmpty() ? List.of() : List.of(root);
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
