package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.AuditLogSnapshot;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import kr.lunaf.cloudislands.api.model.IslandJobSnapshot;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandTemplateSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public interface IslandAdminService {
    CompletableFuture<Void> drainNode(String nodeId);
    CompletableFuture<Void> undrainNode(String nodeId);
    CompletableFuture<Void> sweepNode(String nodeId);
    CompletableFuture<Void> migrateIsland(UUID islandId, String targetNode);
    CompletableFuture<Void> snapshotIsland(UUID islandId, String reason);
    CompletableFuture<Void> restoreIsland(UUID islandId, long snapshotNo);
    CompletableFuture<Void> quarantineIsland(UUID islandId, String reason);
    CompletableFuture<Void> repairIsland(UUID islandId, String reason);
    CompletableFuture<RouteTicket> createAdminTeleportTicket(UUID playerUuid, UUID islandId);
    CompletableFuture<java.util.Optional<RouteTicket>> getRouteTicket(UUID ticketId);
    CompletableFuture<Void> clearRoute(UUID playerUuid, UUID ticketId);
    CompletableFuture<List<IslandJobSnapshot>> listJobs();
    CompletableFuture<Void> retryJob(UUID jobId);
    CompletableFuture<Void> cancelJob(UUID jobId);
    CompletableFuture<Void> recoverJobs(String nodeId, long minIdleMillis, int maxJobs);
    CompletableFuture<Void> clearCache();
    CompletableFuture<Void> reload();
    CompletableFuture<List<GlobalEventSnapshot>> listEvents();
    CompletableFuture<List<AuditLogSnapshot>> listAuditLogs();
    CompletableFuture<List<String>> listNodes();
    CompletableFuture<List<IslandNodeSnapshot>> listNodeSnapshots();
    CompletableFuture<List<IslandTemplateSnapshot>> listTemplates();
    CompletableFuture<IslandTemplateSnapshot> upsertTemplate(String templateId, String displayName, boolean enabled, String minNodeVersion);
    CompletableFuture<IslandTemplateSnapshot> enableTemplate(String templateId);
    CompletableFuture<IslandTemplateSnapshot> disableTemplate(String templateId);
    CompletableFuture<MigrationRunSnapshot> scanSuperiorSkyblock2(String path);
    CompletableFuture<MigrationRunSnapshot> dryRunSuperiorSkyblock2(String path);
    CompletableFuture<MigrationRunSnapshot> importSuperiorSkyblock2(String path);
    CompletableFuture<MigrationRunSnapshot> verifySuperiorSkyblock2(String path);
    CompletableFuture<MigrationRunSnapshot> rollbackSuperiorSkyblock2(String path);
}
