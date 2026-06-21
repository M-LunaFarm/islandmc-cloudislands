package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;

public final class CoreSnapshotQueryClient implements SnapshotQueryClient {
    private final CoreApiClient delegate;

    public CoreSnapshotQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<IslandSnapshotRecord>> records(UUID islandId, int limit) {
        requireIsland(islandId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return delegate.listIslandSnapshots(islandId, safeLimit)
            .thenApply(CoreSnapshotJson::records);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
