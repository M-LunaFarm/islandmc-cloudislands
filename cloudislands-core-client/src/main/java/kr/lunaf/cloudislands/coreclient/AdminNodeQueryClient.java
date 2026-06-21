package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminNodeQueryClient {
    CompletableFuture<AdminNodeSummaryView> listNodesSummary();

    CompletableFuture<CoreGuiViews.NodeSummaryView> nodeInfo(String nodeId);

    CompletableFuture<AdminNodeSummaryView> nodeIslandsSummary(String nodeId, int limit);
}
