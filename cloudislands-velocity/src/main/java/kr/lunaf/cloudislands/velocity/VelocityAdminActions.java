package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import net.kyori.adventure.text.Component;

public final class VelocityAdminActions extends VelocityActionSupport {
    private final VelocityPlayerProgressionActions snapshots;

    VelocityAdminActions(VelocityActionContext context, VelocityPlayerProgressionActions snapshots) {
        super(context);
        this.snapshots = snapshots;
    }

    public void dashboard(Player player) {
        CompletableFuture<CharSequence> metrics = adminPart(coreApiClient.adminMetrics().summary().thenApply(coreStatusMessages::metrics));
        CompletableFuture<CharSequence> nodes = adminPart(coreApiClient.adminNodes().listNodesSummary().thenApply(summary -> "Nodes: " + summary.text()));
        CompletableFuture<CharSequence> jobs = adminPart(coreApiClient.jobs().list().thenApply(nodeJobMessages::jobList));
        CompletableFuture<CharSequence> routes = adminPart(coreApiClient.adminRoutes().debug(new UUID(0L, 0L)).thenApply(this::routeDebugMessage));
        CompletableFuture<CharSequence> storage = adminPart(coreApiClient.adminStorage().status().thenApply(nodeJobMessages::storageStatus));
        CompletableFuture<CharSequence> integrations = adminPart(coreApiClient.adminNodes().integrationSummary().thenApply(summary -> "Integrations: " + summary.text()));
        sendComposite(player, "Dashboard", List.of(metrics, nodes, jobs, routes, storage, integrations));
    }

    public void doctor(Player player) {
        CompletableFuture<CharSequence> config = doctorPart("core-config", coreApiClient.adminCoreConfig().config().thenApply(coreConfigMessages::format));
        CompletableFuture<CharSequence> metrics = doctorPart("metrics", coreApiClient.adminMetrics().summary().thenApply(coreStatusMessages::metrics));
        CompletableFuture<CharSequence> storage = doctorPart("storage", coreApiClient.adminStorage().status().thenApply(nodeJobMessages::storageStatus));
        CompletableFuture<CharSequence> nodes = doctorPart("nodes", coreApiClient.adminNodes().listNodesSummary().thenApply(summary -> "Nodes: " + summary.text()));
        CompletableFuture<CharSequence> jobs = doctorPart("jobs", coreApiClient.jobs().list().thenApply(nodeJobMessages::jobList));
        CompletableFuture<CharSequence> routes = doctorPart("routes", coreApiClient.adminRoutes().debug(new UUID(0L, 0L)).thenApply(this::routeDebugMessage));
        CompletableFuture<CharSequence> audit = doctorPart("audit", coreApiClient.adminAudit().list(5).thenApply(eventMessages::audit));
        CompletableFuture<CharSequence> integrations = doctorPart("integrations", coreApiClient.adminNodes().integrationSummary().thenApply(summary -> "Integrations: " + summary.text()));
        sendComposite(player, "Doctor", List.of(config, metrics, storage, nodes, jobs, routes, audit, integrations));
    }

    public void setup(Player player, String section) {
        String normalized = section == null || section.isBlank() ? "start" : section.toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("verify")) {
            player.sendMessage(playerComponent("Setup verify delegates to /ciadmin doctor for live PASS/WARN/FAIL checks."));
            doctor(player);
            return;
        }
        player.sendMessage(playerComponent(setupMessage(normalized)));
    }

    private static String setupMessage(String section) {
        return switch (section) {
            case "core" -> "Setup core: configure core-api base-url/token/admin-token, then run /ciadmin config validate, /ciadmin config effective, and /ciadmin doctor.";
            case "redis" -> "Setup redis: configure Redis only as cache/event transport, verify it is not source-of-truth, then run /ciadmin doctor and /ciadmin metrics.";
            case "database" -> "Setup database: configure durable shared JDBC for production, avoid in-memory production state, then run /ciadmin config validate and /ciadmin doctor.";
            case "storage" -> "Setup storage: configure object storage credentials and bucket/prefix, then run /ciadmin storage, /ciadmin support-bundle create, and backup/restore rehearsal gates.";
            case "velocity" -> "Setup velocity: configure forwarding secret, fallback server, island pool names, route wait seconds, and hide-node-names; verify with /ciadmin status and /ciadmin doctor.";
            case "paper-node" -> "Setup paper-node: configure unique node.id, role, core-api, storage, integrations, and supported templates; verify with /ciadmin node list and /ciadmin doctor.";
            default -> "Setup start: complete /ciadmin setup core, redis, database, storage, velocity, paper-node, then run /ciadmin setup verify.";
        };
    }

    public void integrations(Player player) {
        sendTextResult(player, coreApiClient.adminNodes().integrationSummary().thenApply(summary -> "Integrations: " + summary.text()), "Integration 상태를 불러오지 못했습니다.");
    }

    public void supportBundle(Player player) {
        sendTextResult(player, coreApiClient.adminSupportBundle().create().thenApply(this::writeSupportBundle), "Support bundle을 생성하지 못했습니다.");
    }

    public void listJobs(Player player) {
        sendTextResult(player, coreApiClient.jobs().list().thenApply(nodeJobMessages::jobList), "작업 목록을 불러오지 못했습니다.");
    }

    public void retryJob(Player player, UUID jobId) {
        sendTextResult(player, coreApiClient.jobCommands().retry(jobId).thenApply(result -> nodeJobMessages.jobAction("retry", result)), "작업 재시도를 요청하지 못했습니다.");
    }

    public void cancelJob(Player player, UUID jobId) {
        sendTextResult(player, coreApiClient.jobCommands().cancel(jobId).thenApply(result -> nodeJobMessages.jobAction("cancel", result)), "작업 취소를 요청하지 못했습니다.");
    }

    public void recoverJobs(Player player, String nodeId, long minIdleMillis, int maxJobs) {
        sendTextResult(player, coreApiClient.jobCommands().recover(nodeId, minIdleMillis, maxJobs).thenApply(nodeJobMessages::jobRecovery), "작업 복구를 요청하지 못했습니다.");
    }

    public void listNodes(Player player) {
        sendTextResult(player, coreApiClient.adminNodes().nodes().thenApply(nodeJobMessages::nodeListSummary), "노드 목록을 불러오지 못했습니다.");
    }

    public void nodeInfo(Player player, String nodeId) {
        sendTextResult(player, coreApiClient.adminNodes().nodeInfo(nodeId).thenApply(nodeJobMessages::appendLevelScanSummary), "노드 정보를 불러오지 못했습니다.");
    }

    public void nodeIslands(Player player, String nodeId, int limit) {
        sendTextResult(player, coreApiClient.adminNodes().nodeIslandRuntimes(nodeId, Math.max(1, Math.min(limit, 200))).thenApply(nodeJobMessages::nodeIslandList), "노드 섬 현황을 불러오지 못했습니다.");
    }

    public void drainNode(Player player, String nodeId) {
        sendTextResult(player, coreApiClient.adminNodeCommands().drainNode(nodeId).thenApply(result -> nodeJobMessages.nodeActionSummary("Node drain", nodeId, result)), "노드 drain을 요청하지 못했습니다.");
    }

    public void undrainNode(Player player, String nodeId) {
        sendTextResult(player, coreApiClient.adminNodeCommands().undrainNode(nodeId).thenApply(result -> nodeJobMessages.nodeActionSummary("Node undrain", nodeId, result)), "노드 undrain을 요청하지 못했습니다.");
    }

    public void sweepNode(Player player, String nodeId) {
        sendTextResult(player, coreApiClient.adminNodeCommands().sweepNode(nodeId).thenApply(nodeJobMessages::nodeSweep), "노드 장애 스윕을 요청하지 못했습니다.");
    }

    public void kickAllNode(Player player, String nodeId, String reason) {
        coreApiClient.adminNodeCommands().kickAllNode(nodeId, reason).thenAccept(result -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeJobMessages.nodeActionSummary("Node kickall", nodeId, result) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 kickall을 요청하지 못했습니다."));
            return null;
        });
    }

    public void shutdownSafeNode(Player player, String nodeId, String reason) {
        coreApiClient.adminNodeCommands().shutdownNodeSafely(nodeId, reason).thenAccept(result -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeJobMessages.nodeActionSummary("Node shutdown-safe", nodeId, result) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 shutdown-safe를 요청하지 못했습니다."));
            return null;
        });
    }

    public void activateIsland(Player player, UUID islandId) {
        sendTextResult(player, coreApiClient.lifecycle().activateIsland(islandId).thenApply(result -> islandMessages.actionResult("Island activate", islandId.toString(), result)), "섬 활성화를 요청하지 못했습니다.");
    }

    public void activateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> activateIsland(player, islandId));
    }

    public void deactivateIsland(Player player, UUID islandId) {
        sendTextResult(player, coreApiClient.lifecycle().deactivateIsland(islandId).thenApply(result -> islandMessages.actionResult("Island deactivate", islandId.toString(), result)), "섬 비활성화를 요청하지 못했습니다.");
    }

    public void deactivateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> deactivateIsland(player, islandId));
    }

    public void migrateIsland(Player player, UUID islandId, String targetNode) {
        sendTextResult(player, coreApiClient.lifecycle().migrateIsland(islandId, targetNode).thenApply(result -> islandMessages.actionResult("Island migrate", islandId.toString(), result)), "섬 마이그레이션을 요청하지 못했습니다.");
    }

    public void migrateIslandTarget(Player player, String target, String targetNode) {
        adminIslandTarget(player, target, islandId -> migrateIsland(player, islandId, targetNode));
    }

    public void quarantineIsland(Player player, UUID islandId, String reason) {
        sendTextResult(player, coreApiClient.lifecycle().quarantineIsland(islandId, reason).thenApply(result -> islandMessages.actionResult("Island quarantine", islandId.toString(), result)), "섬 격리를 요청하지 못했습니다.");
    }

    public void quarantineIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> quarantineIsland(player, islandId, reason));
    }

    public void adminIslandInfo(Player player, UUID lookupUuid) {
        sendTextResult(player, coreApiClient.adminIslands().info(lookupUuid).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandInfoTarget(Player player, String target) {
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            adminIslandInfo(player, parsed);
            return;
        }
        sendTextResult(player, coreApiClient.adminIslands().infoByName(target).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhere(Player player, UUID islandId) {
        sendTextResult(player, coreApiClient.adminIslands().runtime(islandId).thenApply(islandMessages::runtimeInfo), "섬 위치 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhereTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminIslandWhere(player, islandId));
    }

    public void adminTeleportIsland(Player player, UUID islandId) {
        routeFuture(player, coreApiClient.adminIslandTeleport(player.getUniqueId(), islandId), "섬으로 이동하지 못했습니다.");
    }

    public void adminTeleportIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminTeleportIsland(player, islandId));
    }

    public void adminDeleteIsland(Player player, UUID islandId) {
        sendTextResult(player, coreApiClient.lifecycle().adminDeleteIsland(islandId).thenApply(result -> islandMessages.actionResult("Island delete", islandId.toString(), result)), "섬 삭제를 요청하지 못했습니다.");
    }

    public void adminDeleteIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminDeleteIsland(player, islandId));
    }

    public void repairIsland(Player player, UUID islandId, String reason) {
        sendTextResult(player, coreApiClient.lifecycle().repairIsland(islandId, reason).thenApply(result -> islandMessages.actionResult("Island repair", islandId.toString(), result)), "섬 복구를 요청하지 못했습니다.");
    }

    public void repairIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> repairIsland(player, islandId, reason));
    }

    public void listSnapshotsTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> snapshots.listSnapshots(player, islandId));
    }

    public void snapshotTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> snapshots.snapshot(player, islandId, reason));
    }

    public void restoreTarget(Player player, String target, long snapshotNo) {
        adminIslandTarget(player, target, islandId -> snapshots.restore(player, islandId, snapshotNo));
    }

    public void debugRoutes(Player player, UUID playerUuid) {
        coreApiClient.adminRoutes().debug(playerUuid)
            .thenAccept(result -> player.sendMessage(playerComponent(routeDebugMessage(result))))
            .exceptionally(error -> {
                player.sendMessage(playerComponent("라우트 정보를 불러오지 못했습니다."));
                return null;
            });
    }

    public void debugRoutesTarget(Player player, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            debugRoutes(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void routeTicket(Player player, UUID ticketId) {
        coreApiClient.adminRoutes().ticket(ticketId)
            .thenAccept(result -> player.sendMessage(playerComponent(routeTicketMessage(result))))
            .exceptionally(error -> {
                player.sendMessage(playerComponent("티켓 정보를 불러오지 못했습니다."));
                return null;
            });
    }

    public void routeTicketTarget(Player player, String target) {
        UUID ticketId = parseUuid(target);
        if (!ticketId.equals(new UUID(0L, 0L))) {
            routeTicket(player, ticketId);
            return;
        }
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            coreApiClient.adminRoutes().ticketForPlayer(playerUuid)
                .thenAccept(result -> player.sendMessage(playerComponent(routeTicketMessage(result))))
                .exceptionally(error -> {
                    player.sendMessage(playerComponent("티켓 정보를 불러오지 못했습니다."));
                    return null;
                });
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearRoute(Player player, UUID playerUuid, UUID ticketId) {
        coreApiClient.adminRoutes().clear(playerUuid, ticketId)
            .thenAccept(result -> player.sendMessage(playerComponent(routeClearMessage(result))))
            .exceptionally(error -> {
                player.sendMessage(playerComponent("라우트 정리를 요청하지 못했습니다."));
                return null;
            });
    }

    public void clearRouteTarget(Player player, String target, UUID ticketId) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            clearRoute(player, playerUuid, ticketId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearCache(Player player) {
        sendTextResult(player, coreApiClient.adminMaintenance().clearCache().thenApply(result -> coreStatusMessages.maintenance("Cache clear", result)), "캐시 정리를 요청하지 못했습니다.");
    }

    public void listEvents(Player player) {
        sendTextResult(player, coreApiClient.adminEvents().list(100).thenApply(eventMessages::events), "이벤트 목록을 불러오지 못했습니다.");
    }

    public void listAuditLogs(Player player) {
        sendTextResult(player, coreApiClient.adminAudit().list(100).thenApply(eventMessages::audit), "감사 로그를 불러오지 못했습니다.");
    }

    public void metrics(Player player) {
        sendTextResult(player, coreApiClient.adminMetrics().summary().thenApply(coreStatusMessages::metrics), "Core metrics를 불러오지 못했습니다.");
    }

    public void coreConfig(Player player) {
        sendTextResult(player, coreApiClient.adminCoreConfig().config().thenApply(coreConfigMessages::format), "Core config를 불러오지 못했습니다.");
    }

    public void addonEndpoints(Player player) {
        sendTextResult(player, coreApiClient.adminCoreConfig().config().thenApply(coreStatusMessages::addonEndpoints), "Addon endpoint 상태를 불러오지 못했습니다.");
    }

    public void storageStatus(Player player) {
        sendTextResult(player, coreApiClient.adminStorage().status().thenApply(nodeJobMessages::storageStatus), "Storage 상태를 불러오지 못했습니다.");
    }

    public void addonStateSummary(Player player) {
        sendTextResult(player, coreApiClient.adminAddonState().summary().thenApply(islandMessages::addonStateSummary), "Addon state 상태를 불러오지 못했습니다.");
    }

    public void listBlockValues(Player player) {
        sendTextResult(player, coreApiClient.blockValues().list().thenApply(islandMessages::blockValueList), "블록 가치 목록을 불러오지 못했습니다.");
    }

    public void setBlockValue(Player player, String materialKey, String worth, long levelPoints, long limit) {
        sendTextResult(player, coreApiClient.blockValueCommands().set(player.getUniqueId(), materialKey, worth, levelPoints, limit).thenApply(result -> islandMessages.actionResult("Block value set", materialKey, result)), "블록 가치를 변경하지 못했습니다.");
    }

    public void setGameplayBlockAmount(Player player, String target, String materialKey, long amount) {
        adminIslandTarget(player, target, islandId ->
            sendTextResult(player, coreApiClient.environmentCommands().setLimit(islandId, player.getUniqueId(), "BLOCK_AMOUNT:" + normalizeGameplayKey(materialKey), amount).thenApply(result -> islandMessages.environmentAction("Set block amount", result)), "블록 수량을 변경하지 못했습니다."));
    }

    public void setGameplayEffect(Player player, String target, String effectKey, long amplifier) {
        adminIslandTarget(player, target, islandId ->
            sendTextResult(player, coreApiClient.environmentCommands().setLimit(islandId, player.getUniqueId(), "EFFECT:" + normalizeGameplayKey(effectKey), amplifier).thenApply(result -> islandMessages.environmentAction("Set island effect", result)), "섬 효과를 변경하지 못했습니다."));
    }

    public void setGameplayRate(Player player, String target, String rateKey, long percent) {
        adminIslandTarget(player, target, islandId ->
            sendTextResult(player, coreApiClient.environmentCommands().setLimit(islandId, player.getUniqueId(), rateKey, percent).thenApply(result -> islandMessages.environmentAction("Set gameplay rate", result)), "섬 런타임 배율을 변경하지 못했습니다."));
    }

    public void reload(Player player) {
        sendTextResult(player, coreApiClient.adminMaintenance().reload().thenApply(result -> coreStatusMessages.maintenance("Core reload", result)), "reload를 요청하지 못했습니다.");
    }

    private static String normalizeGameplayKey(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_.:-]+", "_");
        return normalized.isBlank() ? "UNKNOWN" : normalized;
    }

    public void migrateSuperiorSkyblock2(Player player, String action, String path) {
        sendTextResult(player, coreApiClient.migrations().migrateSuperiorSkyblock2(action, path).thenApply(migrationMessages::format), "마이그레이션 명령을 실행하지 못했습니다.");
    }

    public void playerInfo(Player player, UUID playerUuid) {
        sendTextResult(player, coreApiClient.playerProfiles().profile(playerUuid).thenApply(islandMessages::playerInfo), "플레이어 정보를 불러오지 못했습니다.");
    }

    public void playerInfoTarget(Player player, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            playerInfo(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setPlayerIsland(Player player, UUID playerUuid, UUID islandId) {
        sendTextResult(player, coreApiClient.playerProfileCommands().setPrimaryIsland(playerUuid, islandId).thenApply(result -> islandMessages.playerAction("Player setisland", playerUuid.toString(), result)), "플레이어 섬을 설정하지 못했습니다.");
    }

    public void setPlayerIslandTarget(Player player, String target, UUID islandId) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setPlayerIsland(player, playerUuid, islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearPlayerIsland(Player player, UUID playerUuid) {
        sendTextResult(player, coreApiClient.playerProfileCommands().clearPrimaryIsland(playerUuid).thenApply(result -> islandMessages.playerAction("Player clearisland", playerUuid.toString(), result)), "플레이어 섬을 해제하지 못했습니다.");
    }

    public void clearPlayerIslandTarget(Player player, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            clearPlayerIsland(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listTemplates(Player player) {
        sendTextResult(player, coreApiClient.templates().list().thenApply(islandMessages::templateList), "섬 템플릿 목록을 불러오지 못했습니다.");
    }

    public void upsertTemplate(Player player, String templateId, String displayName, boolean enabled, String minNodeVersion) {
        sendTextResult(player, coreApiClient.templateCommands().upsert(templateId, displayName, enabled, minNodeVersion).thenApply(result -> islandMessages.templateAction("Template upsert", templateId, result)), "섬 템플릿을 저장하지 못했습니다.");
    }

    public void enableTemplate(Player player, String templateId) {
        sendTextResult(player, coreApiClient.templateCommands().enable(templateId).thenApply(result -> islandMessages.templateAction("Template enable", templateId, result)), "섬 템플릿을 활성화하지 못했습니다.");
    }

    public void disableTemplate(Player player, String templateId) {
        sendTextResult(player, coreApiClient.templateCommands().disable(templateId).thenApply(result -> islandMessages.templateAction("Template disable", templateId, result)), "섬 템플릿을 비활성화하지 못했습니다.");
    }

    public void verifyTemplateBundle(Player player, String templateId) {
        sendTextResult(player, coreApiClient.templateCommands().verifyBundle(templateId).thenApply(islandMessages::templateBundleVerification), "섬 템플릿 번들을 검증하지 못했습니다.");
    }

    public void setTemplateIcon(Player player, String templateId, String iconMaterial, Integer customModelData) {
        sendTextResult(player, coreApiClient.templates().get(templateId).thenCompose(template -> {
            int resolvedCustomModelData = customModelData == null ? template.iconCustomModelData() : customModelData;
            return coreApiClient.templateCommands().upsert(templateWithCatalogFields(template, template.requiredPermission(), iconMaterial, resolvedCustomModelData, template.creationCost()));
        }).thenApply(result -> islandMessages.templateAction("Template set icon", templateId, result)), "섬 템플릿 아이콘을 변경하지 못했습니다.");
    }

    public void setTemplateCost(Player player, String templateId, String creationCost) {
        sendTextResult(player, coreApiClient.templates().get(templateId).thenCompose(template -> coreApiClient.templateCommands().upsert(templateWithCatalogFields(template, template.requiredPermission(), template.iconMaterial(), template.iconCustomModelData(), creationCost))).thenApply(result -> islandMessages.templateAction("Template set cost", templateId, result)), "섬 템플릿 비용을 변경하지 못했습니다.");
    }

    public void setTemplatePermission(Player player, String templateId, String requiredPermission) {
        sendTextResult(player, coreApiClient.templates().get(templateId).thenCompose(template -> coreApiClient.templateCommands().upsert(templateWithCatalogFields(template, templatePermissionArgument(requiredPermission), template.iconMaterial(), template.iconCustomModelData(), template.creationCost()))).thenApply(result -> islandMessages.templateAction("Template set permission", templateId, result)), "섬 템플릿 권한을 변경하지 못했습니다.");
    }

    public void deleteTemplate(Player player, String templateId, boolean confirm) {
        if (!confirm) {
            player.sendMessage(Component.text("사용법: /ciadmin templates delete <id> --confirm"));
            return;
        }
        sendTextResult(player, coreApiClient.templateCommands().delete(templateId, true).thenApply(accepted -> islandMessages.templateDelete(templateId, accepted)), "섬 템플릿을 삭제하지 못했습니다.");
    }

    public void reorderTemplate(Player player, String templateId, int sortOrder) {
        sendTextResult(player, coreApiClient.templateCommands().reorder(templateId, sortOrder).thenApply(result -> islandMessages.templateAction("Template reorder", templateId, result)), "섬 템플릿 순서를 변경하지 못했습니다.");
    }

    private static TemplateView templateWithCatalogFields(TemplateView template, String requiredPermission, String iconMaterial, int iconCustomModelData, String creationCost) {
        return new TemplateView(
            template.id(),
            template.displayName(),
            template.description(),
            template.category(),
            template.enabled(),
            template.minNodeVersion(),
            requiredPermission,
            iconMaterial,
            iconCustomModelData,
            template.previewImageKey(),
            template.bundleStoragePath(),
            template.bundleChecksum(),
            template.bundleSizeBytes(),
            template.schemaVersion(),
            template.defaultIslandSize(),
            template.spawnWorldOffsetX(),
            template.spawnWorldOffsetY(),
            template.spawnWorldOffsetZ(),
            template.spawnYaw(),
            template.spawnPitch(),
            template.homeName(),
            template.environmentPreset(),
            template.biomeKey(),
            template.borderColor(),
            template.bankInitialBalance(),
            creationCost,
            template.sortOrder(),
            template.tags()
        );
    }

    private static String templatePermissionArgument(String value) {
        if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("clear") || value.equals("-")) {
            return "";
        }
        return value;
    }

    private CompletableFuture<CharSequence> adminPart(CompletableFuture<? extends CharSequence> future) {
        return future.thenApply(value -> value == null || value.toString().isBlank() ? "unavailable" : value)
            .exceptionally(error -> "unavailable: " + error.getClass().getSimpleName());
    }

    private CompletableFuture<CharSequence> doctorPart(String label, CompletableFuture<? extends CharSequence> future) {
        return adminPart(future).thenApply(value -> doctorSeverity(value.toString()) + " " + label + "=" + value);
    }

    private void sendComposite(Player player, String label, List<CompletableFuture<CharSequence>> parts) {
        CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new))
            .thenAccept(_ignored -> player.sendMessage(playerComponent(label + ": " + String.join(" | ", parts.stream().map(CompletableFuture::join).map(CharSequence::toString).toList()))))
            .exceptionally(error -> {
                player.sendMessage(playerComponent(label + ": unavailable"));
                return null;
            });
    }

    private static String doctorSeverity(String body) {
        String value = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        if (value.isBlank() || value.contains("fail") || value.contains("down") || value.contains("unavailable") || value.contains("exception") || value.contains("error=")) {
            return "FAIL";
        }
        if (value.contains("warn") || value.contains("stale") || value.contains("missing=") || value.contains("degraded") || value.contains("failed=")) {
            return "WARN";
        }
        return "PASS";
    }

    private String writeSupportBundle(String coreBundleJson) {
        try {
            Path directory = dataDirectory.resolve("support-bundles");
            Files.createDirectories(directory);
            String timestamp = Instant.now().toString().replace(':', '-');
            Path report = directory.resolve("cloudislands-velocity-support-bundle-" + timestamp + ".zip");
            String redacted = VelocityDiagnosticRedactor.redact(coreBundleJson == null ? "{}" : coreBundleJson);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(report))) {
                writeZipEntry(zip, "core-support-bundle.json", redacted);
                writeZipEntry(zip, "velocity-runtime.txt", velocityRuntimeManifest());
            }
            return "Support bundle created: " + report;
        } catch (IOException exception) {
            return "Support bundle failed: " + exception.getMessage();
        }
    }

    private String velocityRuntimeManifest() {
        return "generatedAt=" + Instant.now() + '\n'
            + "pluginVersion=" + BuildInfo.VERSION + '\n'
            + "hideNodeNames=" + hideNodeNames + '\n'
            + "dataDirectory=" + dataDirectory + '\n';
    }

    private static void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
