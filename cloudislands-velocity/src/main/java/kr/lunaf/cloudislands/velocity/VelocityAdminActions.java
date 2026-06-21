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
        sendBodyResult(player, coreApiClient.listJobs().thenApply(nodeJobMessages::jobList), "작업 목록을 불러오지 못했습니다.");
    }

    public void retryJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.retryJob(jobId).thenApply(body -> nodeJobMessages.jobAction("retry", body)), "작업 재시도를 요청하지 못했습니다.");
    }

    public void cancelJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.cancelJob(jobId).thenApply(body -> nodeJobMessages.jobAction("cancel", body)), "작업 취소를 요청하지 못했습니다.");
    }

    public void recoverJobs(Player player, String nodeId, long minIdleMillis, int maxJobs) {
        sendBodyResult(player, coreApiClient.recoverJobs(nodeId, minIdleMillis, maxJobs).thenApply(body -> nodeJobMessages.jobAction("recover", body)), "작업 복구를 요청하지 못했습니다.");
    }

    public void listNodes(Player player) {
        sendBodyResult(player, coreApiClient.listNodes().thenApply(nodeJobMessages::nodeListSummary), "노드 목록을 불러오지 못했습니다.");
    }

    public void nodeInfo(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.nodeInfo(nodeId).thenApply(nodeJobMessages::appendLevelScanSummary), "노드 정보를 불러오지 못했습니다.");
    }

    public void nodeIslands(Player player, String nodeId, int limit) {
        sendBodyResult(player, coreApiClient.nodeIslands(nodeId, Math.max(1, Math.min(limit, 200))).thenApply(nodeJobMessages::nodeIslandList), "노드 섬 현황을 불러오지 못했습니다.");
    }

    public void drainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.drainNode(nodeId).thenApply(body -> nodeJobMessages.nodeActionSummary("Node drain", nodeId, body)), "노드 drain을 요청하지 못했습니다.");
    }

    public void undrainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.undrainNode(nodeId).thenApply(body -> nodeJobMessages.nodeActionSummary("Node undrain", nodeId, body)), "노드 undrain을 요청하지 못했습니다.");
    }

    public void sweepNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.sweepNode(nodeId).thenApply(nodeJobMessages::nodeSweep), "노드 장애 스윕을 요청하지 못했습니다.");
    }

    public void kickAllNode(Player player, String nodeId, String reason) {
        coreApiClient.kickAllNode(nodeId, reason).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeJobMessages.nodeActionSummary("Node kickall", nodeId, body) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 kickall을 요청하지 못했습니다."));
            return null;
        });
    }

    public void shutdownSafeNode(Player player, String nodeId, String reason) {
        coreApiClient.shutdownNodeSafely(nodeId, reason).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeJobMessages.nodeActionSummary("Node shutdown-safe", nodeId, body) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 shutdown-safe를 요청하지 못했습니다."));
            return null;
        });
    }

    public void activateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.activateIsland(islandId).thenApply(body -> islandMessages.actionResult("Island activate", islandId.toString(), body)), "섬 활성화를 요청하지 못했습니다.");
    }

    public void activateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> activateIsland(player, islandId));
    }

    public void deactivateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.deactivateIsland(islandId).thenApply(body -> islandMessages.actionResult("Island deactivate", islandId.toString(), body)), "섬 비활성화를 요청하지 못했습니다.");
    }

    public void deactivateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> deactivateIsland(player, islandId));
    }

    public void migrateIsland(Player player, UUID islandId, String targetNode) {
        sendBodyResult(player, coreApiClient.migrateIsland(islandId, targetNode).thenApply(body -> islandMessages.actionResult("Island migrate", islandId.toString(), body)), "섬 마이그레이션을 요청하지 못했습니다.");
    }

    public void migrateIslandTarget(Player player, String target, String targetNode) {
        adminIslandTarget(player, target, islandId -> migrateIsland(player, islandId, targetNode));
    }

    public void quarantineIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.quarantineIsland(islandId, reason).thenApply(body -> islandMessages.actionResult("Island quarantine", islandId.toString(), body)), "섬 격리를 요청하지 못했습니다.");
    }

    public void quarantineIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> quarantineIsland(player, islandId, reason));
    }

    public void adminIslandInfo(Player player, UUID lookupUuid) {
        sendBodyResult(player, coreApiClient.adminIslandInfo(lookupUuid).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandInfoTarget(Player player, String target) {
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            adminIslandInfo(player, parsed);
            return;
        }
        sendBodyResult(player, coreApiClient.islandInfoByName(target).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhere(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminIslandWhere(islandId).thenApply(islandMessages::runtimeInfo), "섬 위치 정보를 불러오지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.adminDeleteIsland(islandId).thenApply(body -> islandMessages.actionResult("Island delete", islandId.toString(), body)), "섬 삭제를 요청하지 못했습니다.");
    }

    public void adminDeleteIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminDeleteIsland(player, islandId));
    }

    public void repairIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.repairIsland(islandId, reason).thenApply(body -> islandMessages.actionResult("Island repair", islandId.toString(), body)), "섬 복구를 요청하지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.debugRoutes(playerUuid).thenApply(this::routeDebugMessage), "라우트 정보를 불러오지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.routeTicket(ticketId).thenApply(this::routeTicketMessage), "티켓 정보를 불러오지 못했습니다.");
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
            sendBodyResult(player, coreApiClient.routeTicketForPlayer(playerUuid).thenApply(this::routeTicketMessage), "티켓 정보를 불러오지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.clearCache().thenApply(body -> coreStatusMessages.maintenance("Cache clear", body)), "캐시 정리를 요청하지 못했습니다.");
    }

    public void listEvents(Player player) {
        sendBodyResult(player, coreApiClient.listEvents().thenApply(eventMessages::events), "이벤트 목록을 불러오지 못했습니다.");
    }

    public void listAuditLogs(Player player) {
        sendBodyResult(player, coreApiClient.listAuditLogs().thenApply(eventMessages::audit), "감사 로그를 불러오지 못했습니다.");
    }

    public void metrics(Player player) {
        sendBodyResult(player, coreApiClient.metrics().thenApply(coreStatusMessages::metrics), "Core metrics를 불러오지 못했습니다.");
    }

    public void coreConfig(Player player) {
        sendBodyResult(player, coreApiClient.coreConfig().thenApply(coreConfigMessages::format), "Core config를 불러오지 못했습니다.");
    }

    public void addonEndpoints(Player player) {
        sendBodyResult(player, coreApiClient.coreConfig().thenApply(coreStatusMessages::addonEndpoints), "Addon endpoint 상태를 불러오지 못했습니다.");
    }

    public void storageStatus(Player player) {
        sendBodyResult(player, coreApiClient.storageStatus().thenApply(nodeJobMessages::storageStatus), "Storage 상태를 불러오지 못했습니다.");
    }

    public void addonStateSummary(Player player) {
        sendBodyResult(player, coreApiClient.addonStateSummary().thenApply(islandMessages::addonStateSummary), "Addon state 상태를 불러오지 못했습니다.");
    }

    public void listBlockValues(Player player) {
        sendBodyResult(player, coreApiClient.listBlockValues().thenApply(islandMessages::blockValueList), "블록 가치 목록을 불러오지 못했습니다.");
    }

    public void setBlockValue(Player player, String materialKey, String worth, long levelPoints, long limit) {
        sendBodyResult(player, coreApiClient.setBlockValueResult(player.getUniqueId(), materialKey, worth, levelPoints, limit).thenApply(body -> islandMessages.actionResult("Block value set", materialKey, body)), "블록 가치를 변경하지 못했습니다.");
    }

    public void reload(Player player) {
        sendBodyResult(player, coreApiClient.reload().thenApply(body -> coreStatusMessages.maintenance("Core reload", body)), "reload를 요청하지 못했습니다.");
    }

    public void migrateSuperiorSkyblock2(Player player, String action, String path) {
        sendBodyResult(player, coreApiClient.migrateSuperiorSkyblock2(action, path).thenApply(migrationMessages::format), "마이그레이션 명령을 실행하지 못했습니다.");
    }

    public void playerInfo(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.playerInfo(playerUuid).thenApply(islandMessages::playerInfo), "플레이어 정보를 불러오지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.setPlayerIsland(playerUuid, islandId).thenApply(body -> islandMessages.actionResult("Player setisland", playerUuid.toString(), body)), "플레이어 섬을 설정하지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.clearPlayerIsland(playerUuid).thenApply(body -> islandMessages.actionResult("Player clearisland", playerUuid.toString(), body)), "플레이어 섬을 해제하지 못했습니다.");
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
        sendBodyResult(player, coreApiClient.listTemplates().thenApply(islandMessages::templateList), "섬 템플릿 목록을 불러오지 못했습니다.");
    }

    public void upsertTemplate(Player player, String templateId, String displayName, boolean enabled, String minNodeVersion) {
        sendBodyResult(player, coreApiClient.upsertTemplate(templateId, displayName, enabled, minNodeVersion).thenApply(body -> islandMessages.actionResult("Template upsert", templateId, body)), "섬 템플릿을 저장하지 못했습니다.");
    }

    public void enableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.enableTemplate(templateId).thenApply(body -> islandMessages.actionResult("Template enable", templateId, body)), "섬 템플릿을 활성화하지 못했습니다.");
    }

    public void disableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.disableTemplate(templateId).thenApply(body -> islandMessages.actionResult("Template disable", templateId, body)), "섬 템플릿을 비활성화하지 못했습니다.");
    }
}
