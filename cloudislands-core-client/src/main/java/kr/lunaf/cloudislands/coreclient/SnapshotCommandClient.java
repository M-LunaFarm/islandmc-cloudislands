package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SnapshotCommandClient {
    default CompletableFuture<SnapshotActionView> recordSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId) {
        return recordSnapshot(islandId, snapshotNo, storagePath, reason, checksum, sizeBytes, nodeId, 0L);
    }

    CompletableFuture<SnapshotActionView> recordSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken);

    CompletableFuture<SnapshotActionView> requestSnapshot(UUID islandId, String reason);

    CompletableFuture<SnapshotActionView> restoreSnapshot(UUID islandId, long snapshotNo);
}
