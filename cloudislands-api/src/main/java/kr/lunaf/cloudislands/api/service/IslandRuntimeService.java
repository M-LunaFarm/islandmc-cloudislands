package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.ClaimedIslandJobSnapshot;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeJobType;

public interface IslandRuntimeService {
    CompletableFuture<IslandRuntimeSnapshot> activate(UUID islandId, String preferredPool);
    CompletableFuture<IslandActionResult> activateResult(UUID islandId, String preferredPool);
    CompletableFuture<Void> deactivate(UUID islandId);
    CompletableFuture<IslandActionResult> deactivateResult(UUID islandId);
    CompletableFuture<Void> heartbeat(String nodeId, NodeHeartbeat heartbeat);
    CompletableFuture<IslandActionResult> heartbeatResult(String nodeId, NodeHeartbeat heartbeat);
    CompletableFuture<Void> recordBlockDelta(UUID islandId, String materialKey, long delta);
    CompletableFuture<IslandActionResult> recordBlockDeltaResult(UUID islandId, String materialKey, long delta);
    CompletableFuture<List<ClaimedIslandJobSnapshot>> claimJobs(String nodeId, List<String> supportedTypes, int maxJobs);
    CompletableFuture<List<ClaimedIslandJobSnapshot>> claimTypedJobs(String nodeId, List<IslandRuntimeJobType> supportedTypes, int maxJobs);
    CompletableFuture<Void> completeJob(String nodeId, UUID jobId);
    CompletableFuture<Void> completeJob(String nodeId, UUID jobId, Map<String, String> payload);
    CompletableFuture<IslandActionResult> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload);
    CompletableFuture<Void> failJob(String nodeId, UUID jobId, String errorMessage);
    CompletableFuture<IslandActionResult> failJobResult(String nodeId, UUID jobId, String errorMessage);

    record NodeHeartbeat(int players, int activeIslands, double mspt, int activationQueue, long heapUsedMb, long heapMaxMb) {}
}
