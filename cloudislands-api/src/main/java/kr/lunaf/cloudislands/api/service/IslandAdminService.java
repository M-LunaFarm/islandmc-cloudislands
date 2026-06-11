package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IslandAdminService {
    CompletableFuture<Void> drainNode(String nodeId);
    CompletableFuture<Void> undrainNode(String nodeId);
    CompletableFuture<Void> migrateIsland(UUID islandId, String targetNode);
    CompletableFuture<Void> snapshotIsland(UUID islandId, String reason);
    CompletableFuture<Void> restoreIsland(UUID islandId, long snapshotNo);
    CompletableFuture<Void> quarantineIsland(UUID islandId, String reason);
    CompletableFuture<List<String>> listNodes();
}
