package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public interface RuntimeCommandClient {
    CompletableFuture<RuntimeActionView> publishHeartbeat(NodeHeartbeatRequest request);

    CompletableFuture<RuntimeActionView> recordBlockDelta(UUID islandId, String materialKey, long delta);

    CompletableFuture<RuntimeActionView> replaceBlockCounts(UUID islandId, Map<String, Long> counts);

    CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, Map<String, String> payload);

    CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, String errorMessage);
}
