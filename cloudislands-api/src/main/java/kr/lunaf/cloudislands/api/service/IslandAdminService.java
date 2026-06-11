package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandTemplateSnapshot;

public interface IslandAdminService {
    CompletableFuture<Void> drainNode(String nodeId);
    CompletableFuture<Void> undrainNode(String nodeId);
    CompletableFuture<Void> sweepNode(String nodeId);
    CompletableFuture<Void> migrateIsland(UUID islandId, String targetNode);
    CompletableFuture<Void> snapshotIsland(UUID islandId, String reason);
    CompletableFuture<Void> restoreIsland(UUID islandId, long snapshotNo);
    CompletableFuture<Void> quarantineIsland(UUID islandId, String reason);
    CompletableFuture<Void> repairIsland(UUID islandId, String reason);
    CompletableFuture<Void> retryJob(UUID jobId);
    CompletableFuture<Void> cancelJob(UUID jobId);
    CompletableFuture<Void> recoverJobs(String nodeId, long minIdleMillis, int maxJobs);
    CompletableFuture<Void> clearCache();
    CompletableFuture<Void> reload();
    CompletableFuture<List<String>> listNodes();
    CompletableFuture<List<IslandTemplateSnapshot>> listTemplates();
    CompletableFuture<IslandTemplateSnapshot> upsertTemplate(String templateId, String displayName, boolean enabled, String minNodeVersion);
    CompletableFuture<IslandTemplateSnapshot> enableTemplate(String templateId);
    CompletableFuture<IslandTemplateSnapshot> disableTemplate(String templateId);
}
