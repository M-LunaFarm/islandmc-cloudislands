package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;

final class JdkSnapshotClient implements SnapshotQueryClient, SnapshotCommandClient {
    private final JdkCoreApiClient core;

    JdkSnapshotClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<IslandSnapshotRecord>> records(UUID islandId, int limit) {
        requireId(islandId, "islandId");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return core.post("/v1/islands/snapshots", CoreJsonPayload.object("islandId", islandId, "limit", safeLimit))
            .thenApply(CoreSnapshotJson::records);
    }

    @Override
    public CompletableFuture<SnapshotActionView> recordSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
        requireId(islandId, "islandId");
        if (snapshotNo <= 0L) {
            throw new IllegalArgumentException("positive snapshotNo is required");
        }
        return core.postWithResultBody("/v1/islands/snapshots/record", CoreJsonPayload.object(
                "islandId", islandId,
                "snapshotNo", snapshotNo,
                "storagePath", textOrEmpty(storagePath),
                "reason", snapshotReason(reason),
                "checksum", textOrEmpty(checksum),
                "sizeBytes", Math.max(0L, sizeBytes),
                "nodeId", textOrEmpty(nodeId),
                "fencingToken", fencingToken
            ))
            .thenApply(body -> CoreSnapshotJson.action(body, "SNAPSHOT_RECORDED"));
    }

    @Override
    public CompletableFuture<SnapshotActionView> requestSnapshot(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/snapshot", CoreJsonPayload.object("islandId", islandId, "reason", snapshotReason(reason)))
            .thenApply(body -> CoreSnapshotJson.action(body, "SNAPSHOT_REQUESTED"));
    }

    @Override
    public CompletableFuture<SnapshotActionView> restoreSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        if (snapshotNo <= 0L) {
            throw new IllegalArgumentException("positive snapshotNo is required");
        }
        return core.postWithResultBody("/v1/admin/islands/restore", CoreJsonPayload.object("islandId", islandId, "snapshotNo", snapshotNo))
            .thenApply(body -> CoreSnapshotJson.action(body, "RESTORE_REQUESTED"));
    }

    private static String snapshotReason(String reason) {
        return reason == null || reason.isBlank() ? "manual" : reason.trim();
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
