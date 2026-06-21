package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SnapshotCommandClient {
    CompletableFuture<SnapshotActionView> requestSnapshot(UUID islandId, String reason);

    CompletableFuture<SnapshotActionView> restoreSnapshot(UUID islandId, long snapshotNo);
}
