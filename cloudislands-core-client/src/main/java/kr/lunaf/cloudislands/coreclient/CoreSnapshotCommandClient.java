package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreSnapshotCommandClient implements SnapshotCommandClient {
    private final CoreApiClient delegate;

    public CoreSnapshotCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<SnapshotActionView> recordSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
        requireId(islandId, "islandId");
        if (snapshotNo <= 0L) {
            throw new IllegalArgumentException("positive snapshotNo is required");
        }
        return delegate.recordIslandSnapshot(
                islandId,
                snapshotNo,
                textOrEmpty(storagePath),
                normalizeReason(reason),
                textOrEmpty(checksum),
                Math.max(0L, sizeBytes),
                textOrEmpty(nodeId),
                fencingToken
            )
            .thenApply(body -> snapshotAction(body, "SNAPSHOT_RECORDED"));
    }

    @Override
    public CompletableFuture<SnapshotActionView> requestSnapshot(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return delegate.requestIslandSnapshotResult(islandId, normalizeReason(reason))
            .thenApply(body -> snapshotAction(body, "SNAPSHOT_REQUESTED"));
    }

    @Override
    public CompletableFuture<SnapshotActionView> restoreSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        if (snapshotNo <= 0L) {
            throw new IllegalArgumentException("positive snapshotNo is required");
        }
        return delegate.restoreIslandSnapshotResult(islandId, snapshotNo)
            .thenApply(body -> snapshotAction(body, "RESTORE_REQUESTED"));
    }

    private static SnapshotActionView snapshotAction(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = bool(root, "accepted", true);
        accepted = accepted && !root.containsKey("error") && !Boolean.FALSE.equals(root.get("applied"));
        String code = text(root, "code");
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new SnapshotActionView(accepted, code);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "manual";
        }
        return reason.trim();
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
