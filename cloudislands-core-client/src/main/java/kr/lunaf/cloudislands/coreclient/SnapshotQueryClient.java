package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SnapshotQueryClient {
    CompletableFuture<List<CoreGuiViews.SnapshotView>> listSnapshots(UUID islandId, int limit);
}
