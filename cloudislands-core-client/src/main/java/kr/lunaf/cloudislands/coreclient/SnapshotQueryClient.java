package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;

public interface SnapshotQueryClient {
    CompletableFuture<List<IslandSnapshotRecord>> records(UUID islandId, int limit);

    default CompletableFuture<List<CoreGuiViews.SnapshotView>> listSnapshots(UUID islandId, int limit) {
        return records(islandId, limit).thenApply(snapshots -> snapshots.stream()
            .map(CoreSnapshotJson::view)
            .toList());
    }
}
