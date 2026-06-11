package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import net.kyori.adventure.text.Component;

public final class VelocityRoutingController {
    private final ProxyServer proxy;
    private final CoreApiClient coreApiClient;
    private final String fallbackServer;

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer) {
        this.proxy = proxy;
        this.coreApiClient = coreApiClient;
        this.fallbackServer = fallbackServer;
    }

    public void createIsland(Player player, String templateId) {
        coreApiClient.createIsland(player.getUniqueId(), templateId).thenAccept(result -> {
            if (result == null || !result.accepted()) {
                String code = result == null ? "FAILED" : result.code();
                player.sendMessage(Component.text(messageForCreateFailure(code)));
                return;
            }
            player.sendActionBar(Component.text("섬을 생성하고 있습니다."));
            if (result.ticket() != null) {
                route(player, result.ticket(), "섬으로 이동하지 못했습니다.");
            }
        });
    }

    public void deleteIsland(Player player, UUID islandId) {
        coreApiClient.deleteIsland(player.getUniqueId(), islandId).thenAccept(result -> {
            if (result != null && result.accepted()) {
                player.sendMessage(Component.text("섬을 삭제했습니다."));
                return;
            }
            player.sendMessage(Component.text("섬을 삭제할 수 없습니다."));
        });
    }

    public void resetIsland(Player player, UUID islandId, String reason) {
        coreApiClient.resetIsland(islandId, player.getUniqueId(), reason).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 리셋을 요청했습니다." : body)));
    }

    public void showMyIsland(Player player) {
        coreApiClient.islandInfoByOwner(player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 정보를 불러오지 못했습니다." : body)));
    }

    public void showIslandSettings(Player player, UUID islandId) {
        coreApiClient.islandInfo(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 설정을 불러오지 못했습니다." : body)));
    }

    public void showIslandLevel(Player player, UUID islandId) {
        coreApiClient.islandInfo(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 레벨을 불러오지 못했습니다." : body)));
    }

    public void showIslandWorth(Player player, UUID islandId) {
        coreApiClient.islandInfo(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 가치를 불러오지 못했습니다." : body)));
    }

    public void showIslandSize(Player player, UUID islandId) {
        coreApiClient.islandInfo(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 크기를 불러오지 못했습니다." : body)));
    }

    public void showIslandBorder(Player player, UUID islandId) {
        coreApiClient.islandInfo(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 경계를 불러오지 못했습니다." : body)));
    }

    public void showBiome(Player player, UUID islandId) {
        coreApiClient.islandBiome(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 바이옴을 불러오지 못했습니다." : body)));
    }

    public void setBiome(Player player, UUID islandId, String biomeKey) {
        coreApiClient.setIslandBiome(islandId, player.getUniqueId(), biomeKey).thenRun(() -> player.sendMessage(Component.text("섬 바이옴을 변경했습니다.")));
    }

    public void routeHome(Player player) {
        routeHome(player, "default");
    }

    public void routeHome(Player player, String homeName) {
        coreApiClient.createHomeTicket(player.getUniqueId(), homeName).thenAccept(ticket -> route(player, ticket, "현재 섬 서비스 일부 기능이 점검 중입니다."));
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        coreApiClient.createVisitTicket(player.getUniqueId(), targetIslandId).thenAccept(ticket -> route(player, ticket, "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요."));
    }

    public void routeRandomVisit(Player player) {
        coreApiClient.createRandomVisitTicket(player.getUniqueId()).thenAccept(ticket -> route(player, ticket, "방문 가능한 공개 섬을 찾지 못했습니다."));
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        coreApiClient.createWarpTicket(player.getUniqueId(), targetIslandId, warpName).thenAccept(ticket -> route(player, ticket, "해당 워프로 이동할 수 없습니다."));
    }

    public void listWarps(Player player, UUID islandId) {
        coreApiClient.listIslandWarps(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 워프를 불러오지 못했습니다." : body)));
    }

    public void setWarp(Player player, UUID islandId, String name, boolean publicAccess) {
        IslandLocation defaultLocation = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, defaultLocation, publicAccess).thenRun(() -> player.sendMessage(Component.text("섬 워프를 설정했습니다.")));
    }

    public void deleteWarp(Player player, UUID islandId, String name) {
        coreApiClient.deleteIslandWarp(islandId, player.getUniqueId(), name).thenRun(() -> player.sendMessage(Component.text("섬 워프를 삭제했습니다.")));
    }

    public void setWarpPublicAccess(Player player, UUID islandId, String name, boolean publicAccess) {
        coreApiClient.setIslandWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess).thenRun(() -> player.sendMessage(Component.text(publicAccess ? "섬 워프를 공개했습니다." : "섬 워프를 비공개로 변경했습니다.")));
    }

    public void invite(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "초대를 생성하지 못했습니다." : "섬 초대를 보냈습니다.")));
    }

    public void listInvites(Player player) {
        coreApiClient.listPendingInvites(player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "대기 중인 초대가 없습니다." : body)));
    }

    public void acceptInvite(Player player, UUID inviteId) {
        coreApiClient.acceptIslandInvite(inviteId, player.getUniqueId()).thenRun(() -> player.sendMessage(Component.text("섬 초대를 수락했습니다.")));
    }

    public void declineInvite(Player player, UUID inviteId) {
        coreApiClient.declineIslandInvite(inviteId, player.getUniqueId()).thenRun(() -> player.sendMessage(Component.text("섬 초대를 거절했습니다.")));
    }

    public void listMembers(Player player, UUID islandId) {
        coreApiClient.listIslandMembers(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "멤버 목록을 불러오지 못했습니다." : body)));
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, IslandRole role) {
        coreApiClient.setIslandMember(islandId, player.getUniqueId(), targetUuid, role).thenRun(() -> player.sendMessage(Component.text("섬 멤버 역할을 변경했습니다.")));
    }

    public void transferOwnership(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.transferIslandOwnership(islandId, player.getUniqueId(), targetUuid).thenRun(() -> player.sendMessage(Component.text("섬 소유권을 양도했습니다.")));
    }

    public void kickMember(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.removeIslandMember(islandId, player.getUniqueId(), targetUuid).thenRun(() -> player.sendMessage(Component.text("섬 멤버를 추방했습니다.")));
    }

    public void banVisitor(Player player, UUID islandId, UUID targetUuid, String reason) {
        coreApiClient.banIslandVisitor(islandId, player.getUniqueId(), targetUuid, reason).thenRun(() -> player.sendMessage(Component.text("방문자를 밴했습니다.")));
    }

    public void listBans(Player player, UUID islandId) {
        coreApiClient.listIslandBans(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "밴 목록을 불러오지 못했습니다." : body)));
    }

    public void pardonVisitor(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.pardonIslandVisitor(islandId, player.getUniqueId(), targetUuid).thenRun(() -> player.sendMessage(Component.text("방문자 밴을 해제했습니다.")));
    }

    public void kickVisitor(Player player, UUID islandId, UUID targetUuid) {
        proxy.getPlayer(targetUuid).ifPresentOrElse(target -> {
            target.sendMessage(Component.text("섬에서 추방되어 로비로 이동합니다."));
            fallback(target, "섬에서 추방되어 로비로 이동합니다.");
            player.sendMessage(Component.text("방문자를 섬에서 추방했습니다."));
        }, () -> player.sendMessage(Component.text("대상 플레이어가 온라인이 아닙니다.")));
    }

    public void setPublicAccess(Player player, UUID islandId, boolean publicAccess) {
        coreApiClient.setIslandPublicAccess(islandId, player.getUniqueId(), publicAccess).thenRun(() -> player.sendMessage(Component.text(publicAccess ? "섬을 공개로 변경했습니다." : "섬을 비공개로 변경했습니다.")));
    }

    public void setFlyFlag(Player player, UUID islandId, boolean enabled) {
        coreApiClient.setIslandFlag(islandId, player.getUniqueId(), kr.lunaf.cloudislands.api.model.IslandFlag.FLY, Boolean.toString(enabled))
            .thenRun(() -> player.sendMessage(Component.text(enabled ? "섬 비행을 허용했습니다." : "섬 비행을 비활성화했습니다.")));
    }

    public void setBooleanFlag(Player player, UUID islandId, kr.lunaf.cloudislands.api.model.IslandFlag flag, boolean enabled, String label) {
        coreApiClient.setIslandFlag(islandId, player.getUniqueId(), flag, Boolean.toString(enabled))
            .thenRun(() -> player.sendMessage(Component.text("섬 " + label + " 설정을 " + (enabled ? "켰습니다." : "껐습니다."))));
    }

    public void listHomes(Player player, UUID islandId) {
        coreApiClient.listIslandHomes(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 홈을 불러오지 못했습니다." : body)));
    }

    public void setHome(Player player, UUID islandId, String name) {
        IslandLocation defaultHome = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, defaultHome).thenRun(() -> player.sendMessage(Component.text("섬 홈을 설정했습니다.")));
    }

    public void setLocked(Player player, UUID islandId, boolean locked) {
        coreApiClient.setIslandLocked(islandId, player.getUniqueId(), locked).thenRun(() -> player.sendMessage(Component.text(locked ? "섬을 잠금 상태로 변경했습니다." : "섬 잠금을 해제했습니다.")));
    }

    public void listPermissions(Player player, UUID islandId) {
        coreApiClient.listIslandPermissions(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 권한을 불러오지 못했습니다." : body)));
    }

    public void setPermission(Player player, UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        coreApiClient.setIslandPermission(islandId, player.getUniqueId(), role, permission, allowed).thenRun(() -> player.sendMessage(Component.text("섬 권한을 변경했습니다.")));
    }

    public void listIslandLogs(Player player, UUID islandId) {
        coreApiClient.listIslandLogs(islandId, 30).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 로그를 불러오지 못했습니다." : body)));
    }

    public void showBank(Player player, UUID islandId) {
        coreApiClient.islandBank(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 은행을 불러오지 못했습니다." : body)));
    }

    public void depositBank(Player player, UUID islandId, String amount) {
        coreApiClient.depositIslandBank(islandId, player.getUniqueId(), amount).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "입금에 실패했습니다." : body)));
    }

    public void withdrawBank(Player player, UUID islandId, String amount) {
        coreApiClient.withdrawIslandBank(islandId, player.getUniqueId(), amount).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "출금에 실패했습니다." : body)));
    }

    public void showLevelRanking(Player player) {
        coreApiClient.topIslandsByLevel(10).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "랭킹을 불러오지 못했습니다." : body)));
    }

    public void showWorthRanking(Player player) {
        coreApiClient.topIslandsByWorth(10).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "가치 랭킹을 불러오지 못했습니다." : body)));
    }

    public void recalculateLevel(Player player, UUID islandId) {
        coreApiClient.recalculateIslandLevel(islandId, player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "레벨 계산을 시작하지 못했습니다." : body)));
    }

    public void listUpgradeRules(Player player) {
        coreApiClient.listUpgradeRules().thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "업그레이드 목록을 불러오지 못했습니다." : body)));
    }

    public void listUpgrades(Player player, UUID islandId) {
        coreApiClient.listIslandUpgrades(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 업그레이드를 불러오지 못했습니다." : body)));
    }

    public void purchaseUpgrade(Player player, UUID islandId, String upgradeKey) {
        coreApiClient.purchaseIslandUpgrade(islandId, player.getUniqueId(), upgradeKey).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "업그레이드에 실패했습니다." : body)));
    }

    public void listMissions(Player player, UUID islandId) {
        coreApiClient.listIslandMissions(islandId, "MISSION").thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "미션 목록을 불러오지 못했습니다." : body)));
    }

    public void listChallenges(Player player, UUID islandId) {
        coreApiClient.listIslandMissions(islandId, "CHALLENGE").thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "챌린지 목록을 불러오지 못했습니다." : body)));
    }

    public void completeMission(Player player, UUID islandId, String missionKey) {
        coreApiClient.completeIslandMission(islandId, player.getUniqueId(), missionKey).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "미션을 완료하지 못했습니다." : body)));
    }

    public void listLimits(Player player, UUID islandId) {
        coreApiClient.listIslandLimits(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 제한을 불러오지 못했습니다." : body)));
    }

    public void setLimit(Player player, UUID islandId, String limitKey, long value) {
        coreApiClient.setIslandLimit(islandId, player.getUniqueId(), limitKey, value).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 제한을 변경하지 못했습니다." : body)));
    }

    public void sendIslandChat(Player player, UUID islandId, String channel, String message) {
        coreApiClient.sendIslandChat(islandId, player.getUniqueId(), channel, message).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 채팅을 전송하지 못했습니다." : body)));
    }

    public void listSnapshots(Player player, UUID islandId) {
        coreApiClient.listIslandSnapshots(islandId, 20).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "스냅샷 목록을 불러오지 못했습니다." : body)));
    }

    public void snapshot(Player player, UUID islandId, String reason) {
        coreApiClient.requestIslandSnapshot(islandId, reason).thenRun(() -> player.sendMessage(Component.text("섬 스냅샷을 요청했습니다.")));
    }

    public void restore(Player player, UUID islandId, long snapshotNo) {
        coreApiClient.restoreIslandSnapshot(islandId, snapshotNo).thenRun(() -> player.sendMessage(Component.text("섬 복원을 요청했습니다.")));
    }

    public void listJobs(Player player) {
        coreApiClient.listJobs().thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "작업 목록을 불러오지 못했습니다." : body)));
    }

    public void retryJob(Player player, UUID jobId) {
        coreApiClient.retryJob(jobId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "작업 재시도를 요청하지 못했습니다." : body)));
    }

    public void cancelJob(Player player, UUID jobId) {
        coreApiClient.cancelJob(jobId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "작업 취소를 요청하지 못했습니다." : body)));
    }

    public void recoverJobs(Player player, String nodeId, long minIdleMillis, int maxJobs) {
        coreApiClient.recoverJobs(nodeId, minIdleMillis, maxJobs).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "작업 복구를 요청하지 못했습니다." : body)));
    }

    public void listNodes(Player player) {
        coreApiClient.listNodes().thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "노드 목록을 불러오지 못했습니다." : body)));
    }

    public void nodeInfo(Player player, String nodeId) {
        coreApiClient.nodeInfo(nodeId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "노드 정보를 불러오지 못했습니다." : body)));
    }

    public void drainNode(Player player, String nodeId) {
        coreApiClient.drainNode(nodeId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "노드 drain을 요청하지 못했습니다." : body)));
    }

    public void undrainNode(Player player, String nodeId) {
        coreApiClient.undrainNode(nodeId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "노드 undrain을 요청하지 못했습니다." : body)));
    }

    public void sweepNode(Player player, String nodeId) {
        coreApiClient.sweepNode(nodeId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "노드 장애 스윕을 요청하지 못했습니다." : body)));
    }

    public void kickAllNode(Player player, String nodeId) {
        int moved = moveNodePlayersToFallback(nodeId);
        player.sendMessage(Component.text("노드 플레이어 " + moved + "명을 로비로 이동시켰습니다."));
    }

    public void shutdownSafeNode(Player player, String nodeId) {
        coreApiClient.drainNode(nodeId).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text("노드를 drain 처리하고 플레이어 " + moved + "명을 로비로 이동시켰습니다."));
        });
    }

    public void activateIsland(Player player, UUID islandId) {
        coreApiClient.activateIsland(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 활성화를 요청하지 못했습니다." : body)));
    }

    public void deactivateIsland(Player player, UUID islandId) {
        coreApiClient.deactivateIsland(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 비활성화를 요청하지 못했습니다." : body)));
    }

    public void migrateIsland(Player player, UUID islandId, String targetNode) {
        coreApiClient.migrateIsland(islandId, targetNode).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 마이그레이션을 요청하지 못했습니다." : body)));
    }

    public void quarantineIsland(Player player, UUID islandId, String reason) {
        coreApiClient.quarantineIsland(islandId, reason).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 격리를 요청하지 못했습니다." : body)));
    }

    public void adminIslandInfo(Player player, UUID lookupUuid) {
        coreApiClient.adminIslandInfo(lookupUuid).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 정보를 불러오지 못했습니다." : body)));
    }

    public void adminIslandWhere(Player player, UUID islandId) {
        coreApiClient.adminIslandWhere(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 위치 정보를 불러오지 못했습니다." : body)));
    }

    public void adminTeleportIsland(Player player, UUID islandId) {
        coreApiClient.adminIslandTeleport(player.getUniqueId(), islandId).thenAccept(ticket -> route(player, ticket, "섬으로 이동하지 못했습니다."));
    }

    public void adminDeleteIsland(Player player, UUID islandId) {
        coreApiClient.adminDeleteIsland(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 삭제를 요청하지 못했습니다." : body)));
    }

    public void repairIsland(Player player, UUID islandId, String reason) {
        coreApiClient.repairIsland(islandId, reason).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 복구를 요청하지 못했습니다." : body)));
    }

    public void debugRoutes(Player player, UUID playerUuid) {
        coreApiClient.debugRoutes(playerUuid).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "라우트 정보를 불러오지 못했습니다." : body)));
    }

    public void routeTicket(Player player, UUID ticketId) {
        coreApiClient.routeTicket(ticketId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "티켓 정보를 불러오지 못했습니다." : body)));
    }

    public void clearRoute(Player player, UUID playerUuid, UUID ticketId) {
        coreApiClient.clearRoute(playerUuid, ticketId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "라우트 정리를 요청하지 못했습니다." : body)));
    }

    public void clearCache(Player player) {
        coreApiClient.clearCache().thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "캐시 정리를 요청하지 못했습니다." : body)));
    }

    public void reload(Player player) {
        coreApiClient.reload().thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "reload를 요청하지 못했습니다." : body)));
    }

    public void migrateSuperiorSkyblock2(Player player, String action, String path) {
        coreApiClient.migrateSuperiorSkyblock2(action, path).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "마이그레이션 명령을 실행하지 못했습니다." : body)));
    }

    public void playerInfo(Player player, UUID playerUuid) {
        coreApiClient.playerInfo(playerUuid).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "플레이어 정보를 불러오지 못했습니다." : body)));
    }

    public void setPlayerIsland(Player player, UUID playerUuid, UUID islandId) {
        coreApiClient.setPlayerIsland(playerUuid, islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "플레이어 섬을 설정하지 못했습니다." : body)));
    }

    public void clearPlayerIsland(Player player, UUID playerUuid) {
        coreApiClient.clearPlayerIsland(playerUuid).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "플레이어 섬을 해제하지 못했습니다." : body)));
    }

    public void listTemplates(Player player) {
        coreApiClient.listTemplates().thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 템플릿 목록을 불러오지 못했습니다." : body)));
    }

    public void upsertTemplate(Player player, String templateId, String displayName, boolean enabled, String minNodeVersion) {
        coreApiClient.upsertTemplate(templateId, displayName, enabled, minNodeVersion).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 템플릿을 저장하지 못했습니다." : body)));
    }

    public void enableTemplate(Player player, String templateId) {
        coreApiClient.enableTemplate(templateId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 템플릿을 활성화하지 못했습니다." : body)));
    }

    public void disableTemplate(Player player, String templateId) {
        coreApiClient.disableTemplate(templateId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 템플릿을 비활성화하지 못했습니다." : body)));
    }

    private void route(Player player, RouteTicket ticket, String failureMessage) {
        if (ticket == null) {
            fallback(player, failureMessage);
            return;
        }
        if (ticket.state().name().equals("PREPARING")) {
            player.sendActionBar(Component.text("섬을 준비하는 중입니다."));
            waitForReadyTicket(player, ticket, failureMessage, 0);
            return;
        }
        publishAndConnect(player, ticket);
    }

    private void waitForReadyTicket(Player player, RouteTicket ticket, String failureMessage, int attempt) {
        int progress = Math.min(95, 20 + attempt);
        player.sendActionBar(Component.text("섬을 준비하는 중입니다... " + progress + "%"));
        coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
            Optional<RouteTicket> ready = status.filter(value -> value.state().name().equals("READY"));
            if (ready.isPresent()) {
                player.sendActionBar(Component.text("잠시 후 섬으로 이동합니다."));
                publishAndConnect(player, ready.get());
                return;
            }
            if (attempt >= 60) {
                fallback(player, failureMessage);
                return;
            }
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> waitForReadyTicket(player, ticket, failureMessage, attempt + 1));
        });
    }

    private void publishAndConnect(Player player, RouteTicket ticket) {
        coreApiClient.publishRouteSession(ticket).thenRun(() -> {
            String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
            connectWithTicket(player, targetServerName);
        });
    }

    private void connectWithTicket(Player player, String targetServerName) {
        proxy.getServer(targetServerName).ifPresentOrElse(server -> connect(player, server), () -> fallback(player, "섬 서버를 찾을 수 없습니다."));
    }

    private void connect(Player player, RegisteredServer server) {
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                fallback(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.");
            }
        });
    }

    private void fallback(Player player, String message) {
        player.sendMessage(Component.text(message));
        proxy.getServer(fallbackServer).ifPresent(server -> player.createConnectionRequest(server).connectWithIndication());
    }

    private int moveNodePlayersToFallback(String nodeId) {
        RegisteredServer target = findServer(nodeId);
        RegisteredServer fallback = findServer(fallbackServer);
        if (target == null || fallback == null) {
            return 0;
        }
        java.util.List<Player> players = java.util.List.copyOf(target.getPlayersConnected());
        for (Player connected : players) {
            connected.sendMessage(Component.text("섬 서버 점검으로 로비로 이동합니다."));
            connected.createConnectionRequest(fallback).connectWithIndication();
        }
        return players.size();
    }

    private RegisteredServer findServer(String name) {
        return proxy.getServer(name).or(() -> proxy.getServer(nodeIdToServerName(name))).orElse(null);
    }

    private String nodeIdToServerName(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        String[] parts = nodeId.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String messageForCreateFailure(String code) {
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> "이미 섬을 보유하고 있습니다.";
            case "TEMPLATE_UNAVAILABLE" -> "사용할 수 없는 섬 템플릿입니다.";
            case "NODE_UNAVAILABLE" -> "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.";
            default -> "섬 생성에 실패했습니다.";
        };
    }
}
