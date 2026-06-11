package kr.lunaf.cloudislands.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;

public interface IslandRuntimeService {
    CompletableFuture<IslandRuntimeSnapshot> activate(UUID islandId, String preferredPool);
    CompletableFuture<Void> deactivate(UUID islandId);
    CompletableFuture<IslandActionResult> deactivateResult(UUID islandId);
    CompletableFuture<Void> heartbeat(String nodeId, NodeHeartbeat heartbeat);
    CompletableFuture<IslandActionResult> heartbeatResult(String nodeId, NodeHeartbeat heartbeat);
    CompletableFuture<Void> recordBlockDelta(UUID islandId, String materialKey, long delta);
    CompletableFuture<IslandActionResult> recordBlockDeltaResult(UUID islandId, String materialKey, long delta);

    record NodeHeartbeat(int players, int activeIslands, double mspt, int activationQueue, long heapUsedMb, long heapMaxMb) {}
}
