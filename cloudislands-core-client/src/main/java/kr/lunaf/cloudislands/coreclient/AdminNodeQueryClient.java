package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;

public interface AdminNodeQueryClient {
    CompletableFuture<List<IslandNodeSnapshot>> nodes();

    CompletableFuture<AdminNodeSummaryView> listNodesSummary();

    CompletableFuture<Optional<IslandNodeSnapshot>> nodeSnapshot(String nodeId);

    CompletableFuture<CoreGuiViews.NodeSummaryView> nodeInfo(String nodeId);

    CompletableFuture<List<AdminIslandRuntimeView>> nodeIslandRuntimes(String nodeId, int limit);

    CompletableFuture<AdminNodeSummaryView> nodeIslandsSummary(String nodeId, int limit);
}
