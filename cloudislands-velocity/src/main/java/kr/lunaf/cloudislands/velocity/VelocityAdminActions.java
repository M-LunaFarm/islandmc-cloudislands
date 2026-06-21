package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.parseLong;
import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import net.kyori.adventure.text.Component;

public final class VelocityAdminActions extends VelocityActionSupport {
    private final VelocityPlayerProgressionActions snapshots;

    VelocityAdminActions(VelocityActionContext context, VelocityPlayerProgressionActions snapshots) {
        super(context);
        this.snapshots = snapshots;
    }

    public void listJobs(Player player) {
        sendBodyResult(player, coreApiClient.jobs().list().thenApply(nodeJobMessages::jobList), "작업 목록을 불러오지 못했습니다.");
    }

    public void retryJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.jobCommands().retry(jobId).thenApply(result -> nodeJobMessages.jobAction("retry", result)), "작업 재시도를 요청하지 못했습니다.");
    }

    public void cancelJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.jobCommands().cancel(jobId).thenApply(result -> nodeJobMessages.jobAction("cancel", result)), "작업 취소를 요청하지 못했습니다.");
    }

    public void recoverJobs(Player player, String nodeId, long minIdleMillis, int maxJobs) {
        sendBodyResult(player, coreApiClient.jobCommands().recover(nodeId, minIdleMillis, maxJobs).thenApply(nodeJobMessages::jobRecovery), "작업 복구를 요청하지 못했습니다.");
    }

    public void listNodes(Player player) {
        sendBodyResult(player, coreApiClient.adminNodes().nodes().thenApply(nodeJobMessages::nodeListSummary), "노드 목록을 불러오지 못했습니다.");
    }

    public void nodeInfo(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.adminNodes().nodeInfo(nodeId).thenApply(nodeJobMessages::appendLevelScanSummary), "노드 정보를 불러오지 못했습니다.");
    }

    public void nodeIslands(Player player, String nodeId, int limit) {
        sendBodyResult(player, coreApiClient.adminNodes().nodeIslandRuntimes(nodeId, Math.max(1, Math.min(limit, 200))).thenApply(nodeJobMessages::nodeIslandList), "노드 섬 현황을 불러오지 못했습니다.");
    }

    public void drainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.adminNodeCommands().drainNode(nodeId).thenApply(result -> nodeJobMessages.nodeActionSummary("Node drain", nodeId, result)), "노드 drain을 요청하지 못했습니다.");
    }

    public void undrainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.adminNodeCommands().undrainNode(nodeId).thenApply(result -> nodeJobMessages.nodeActionSummary("Node undrain", nodeId, result)), "노드 undrain을 요청하지 못했습니다.");
    }

    public void sweepNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.adminNodeCommands().sweepNode(nodeId).thenApply(nodeJobMessages::nodeSweep), "노드 장애 스윕을 요청하지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.lifecycle().activateIsland(islandId).thenApply(result -> islandMessages.actionResult("Island activate", islandId.toString(), result)), "섬 활성화를 요청하지 못했습니다.");
    }

    public void activateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> activateIsland(player, islandId));
    }

    public void deactivateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.lifecycle().deactivateIsland(islandId).thenApply(result -> islandMessages.actionResult("Island deactivate", islandId.toString(), result)), "섬 비활성화를 요청하지 못했습니다.");
    }

    public void deactivateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> deactivateIsland(player, islandId));
    }

    public void migrateIsland(Player player, UUID islandId, String targetNode) {
        sendBodyResult(player, coreApiClient.lifecycle().migrateIsland(islandId, targetNode).thenApply(result -> islandMessages.actionResult("Island migrate", islandId.toString(), result)), "섬 마이그레이션을 요청하지 못했습니다.");
    }

    public void migrateIslandTarget(Player player, String target, String targetNode) {
        adminIslandTarget(player, target, islandId -> migrateIsland(player, islandId, targetNode));
    }

    public void quarantineIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.lifecycle().quarantineIsland(islandId, reason).thenApply(result -> islandMessages.actionResult("Island quarantine", islandId.toString(), result)), "섬 격리를 요청하지 못했습니다.");
    }

    public void quarantineIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> quarantineIsland(player, islandId, reason));
    }

    public void adminIslandInfo(Player player, UUID lookupUuid) {
        sendBodyResult(player, coreApiClient.adminIslands().info(lookupUuid).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandInfoTarget(Player player, String target) {
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            adminIslandInfo(player, parsed);
            return;
        }
        sendBodyResult(player, coreApiClient.adminIslands().infoByName(target).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhere(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminIslands().runtime(islandId).thenApply(islandMessages::runtimeInfo), "섬 위치 정보를 불러오지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.lifecycle().adminDeleteIsland(islandId).thenApply(result -> islandMessages.actionResult("Island delete", islandId.toString(), result)), "섬 삭제를 요청하지 못했습니다.");
    }

    public void adminDeleteIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminDeleteIsland(player, islandId));
    }

    public void repairIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.lifecycle().repairIsland(islandId, reason).thenApply(result -> islandMessages.actionResult("Island repair", islandId.toString(), result)), "섬 복구를 요청하지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.adminMaintenance().clearCache().thenApply(result -> coreStatusMessages.maintenance("Cache clear", result)), "캐시 정리를 요청하지 못했습니다.");
    }

    public void listEvents(Player player) {
        sendBodyResult(player, coreApiClient.adminEvents().list(100).thenApply(eventMessages::events), "이벤트 목록을 불러오지 못했습니다.");
    }

    public void listAuditLogs(Player player) {
        sendBodyResult(player, coreApiClient.adminAudit().list(100).thenApply(eventMessages::audit), "감사 로그를 불러오지 못했습니다.");
    }

    public void metrics(Player player) {
        sendBodyResult(player, coreApiClient.adminMetrics().summary().thenApply(coreStatusMessages::metrics), "Core metrics를 불러오지 못했습니다.");
    }

    public void coreConfig(Player player) {
        sendBodyResult(player, coreApiClient.adminCoreConfig().config().thenApply(coreConfigMessages::format), "Core config를 불러오지 못했습니다.");
    }

    public void addonEndpoints(Player player) {
        sendBodyResult(player, coreApiClient.adminCoreConfig().config().thenApply(coreStatusMessages::addonEndpoints), "Addon endpoint 상태를 불러오지 못했습니다.");
    }

    public void storageStatus(Player player) {
        sendBodyResult(player, coreApiClient.adminStorage().status().thenApply(nodeJobMessages::storageStatus), "Storage 상태를 불러오지 못했습니다.");
    }

    public void addonStateSummary(Player player) {
        sendBodyResult(player, coreApiClient.adminAddonState().summary().thenApply(islandMessages::addonStateSummary), "Addon state 상태를 불러오지 못했습니다.");
    }

    public void listBlockValues(Player player) {
        sendBodyResult(player, coreApiClient.blockValues().list().thenApply(islandMessages::blockValueList), "블록 가치 목록을 불러오지 못했습니다.");
    }

    public void setBlockValue(Player player, String materialKey, String worth, long levelPoints, long limit) {
        sendBodyResult(player, coreApiClient.blockValueCommands().set(player.getUniqueId(), materialKey, worth, levelPoints, limit).thenApply(result -> islandMessages.actionResult("Block value set", materialKey, result)), "블록 가치를 변경하지 못했습니다.");
    }

    public void reload(Player player) {
        sendBodyResult(player, coreApiClient.adminMaintenance().reload().thenApply(result -> coreStatusMessages.maintenance("Core reload", result)), "reload를 요청하지 못했습니다.");
    }

    public void migrateSuperiorSkyblock2(Player player, String action, String path) {
        sendBodyResult(player, coreApiClient.migrateSuperiorSkyblock2(action, path).thenApply(migrationMessages::format), "마이그레이션 명령을 실행하지 못했습니다.");
    }

    public void playerInfo(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.playerProfiles().profile(playerUuid).thenApply(islandMessages::playerInfo), "플레이어 정보를 불러오지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.playerProfileCommands().setPrimaryIsland(playerUuid, islandId).thenApply(result -> islandMessages.playerAction("Player setisland", playerUuid.toString(), result)), "플레이어 섬을 설정하지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.playerProfileCommands().clearPrimaryIsland(playerUuid).thenApply(result -> islandMessages.playerAction("Player clearisland", playerUuid.toString(), result)), "플레이어 섬을 해제하지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.templates().list().thenApply(islandMessages::templateList), "섬 템플릿 목록을 불러오지 못했습니다.");
    }

    public void upsertTemplate(Player player, String templateId, String displayName, boolean enabled, String minNodeVersion) {
        sendBodyResult(player, coreApiClient.templateCommands().upsert(templateId, displayName, enabled, minNodeVersion).thenApply(result -> islandMessages.templateAction("Template upsert", templateId, result)), "섬 템플릿을 저장하지 못했습니다.");
    }

    public void enableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.templateCommands().enable(templateId).thenApply(result -> islandMessages.templateAction("Template enable", templateId, result)), "섬 템플릿을 활성화하지 못했습니다.");
    }

    public void disableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.templateCommands().disable(templateId).thenApply(result -> islandMessages.templateAction("Template disable", templateId, result)), "섬 템플릿을 비활성화하지 못했습니다.");
    }
}
