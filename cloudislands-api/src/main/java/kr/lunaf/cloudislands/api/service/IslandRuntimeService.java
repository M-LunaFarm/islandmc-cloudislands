package kr.lunaf.cloudislands.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;

public interface IslandRuntimeService {
    CompletableFuture<IslandRuntimeSnapshot> activate(UUID islandId, String preferredPool);
    CompletableFuture<Void> deactivate(UUID islandId);
    CompletableFuture<Void> heartbeat(String nodeId, NodeHeartbeat heartbeat);

    record NodeHeartbeat(int players, int activeIslands, double mspt, int activationQueue, long heapUsedMb, long heapMaxMb) {}
}
